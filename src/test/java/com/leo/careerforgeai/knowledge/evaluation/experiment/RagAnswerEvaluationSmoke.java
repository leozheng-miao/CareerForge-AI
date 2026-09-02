package com.leo.careerforgeai.knowledge.evaluation.experiment;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.leo.careerforgeai.knowledge.application.context.ContextAssembler;
import com.leo.careerforgeai.knowledge.application.rerank.KnowledgeRerankingService;
import com.leo.careerforgeai.knowledge.application.retrieval.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.application.answer.RagAnswerException;
import com.leo.careerforgeai.knowledge.application.answer.RagAnswerService;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswer;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswerStatus;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankStatus;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.config.ChunkingProperties;
import com.leo.careerforgeai.knowledge.evaluation.dataset.EvaluationCorpusGuard;
import com.leo.careerforgeai.knowledge.evaluation.dataset.EvaluationCorpusManifest;
import com.leo.careerforgeai.knowledge.evaluation.dataset.RetrievalEvaluationDataset;
import com.leo.careerforgeai.knowledge.evaluation.dataset.RetrievalEvaluationDatasetLoader;
import com.leo.careerforgeai.knowledge.infrastructure.document.cleaning.DocumentCleaner;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.knowledge.infrastructure.document.loading.MarkdownDocumentLoader;
import com.leo.careerforgeai.knowledge.config.KnowledgeIndexProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RagAnswerEvaluationSmoke {

    private static final int CANDIDATE_TOP_K = 10;
    private static final int FINAL_TOP_K = 5;
    private static final int NUM_CANDIDATES = 50;
    private static final int MAX_CONTEXT_CHARS = 3_000;
    private static final boolean RERANK_ENABLED = Boolean.getBoolean("rag.answer.rerank.enabled");
    private static final Path REPORT_PATH = Path.of("target/rag-evaluation/rag-answer-evaluation-" + (RERANK_ENABLED ? "qwen3" : "rrf") + ".json");

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private KnowledgeRerankingService rerankingService;

    @Autowired
    private ContextAssembler contextAssembler;

    @Autowired
    private RagAnswerService answerService;

    @Autowired
    private MarkdownDocumentLoader documentLoader;

    @Autowired
    private ChunkingProperties chunkingProperties;

    @Autowired
    private KnowledgeSourceProperties sourceProperties;

    @Autowired
    private KnowledgeIndexProperties indexProperties;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private JsonMapper jsonMapper;

    /** 使用固定 20 条 Case 评测真实问答、拒答、引用合法性和端到端延迟。 */
    @Test
    void shouldEvaluateRealRagAnswersAndRefusals() throws IOException {
        assertThat(elasticsearchClient.indices().existsAlias(request -> request.name(indexProperties.getIndexAlias())).value())
                .as("评测前必须存在已发布的知识库 Alias")
                .isTrue();

        EvaluationCorpusGuard corpusGuard = new EvaluationCorpusGuard(jsonMapper);
        EvaluationCorpusManifest manifest = corpusGuard.loadManifest();
        RetrievalEvaluationDataset dataset = new RetrievalEvaluationDatasetLoader(jsonMapper).load();
        List<SourceDocument> sourceDocuments = documentLoader.loadAll();

        corpusGuard.verify(manifest, sourceDocuments, DocumentCleaner.CLEANING_VERSION, chunkingProperties.chunkerVersion());

        RetrievalScope scope = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of());
        retrievalService.retrieveHybrid("RAG 回答固定评测预热查询", scope, CANDIDATE_TOP_K, NUM_CANDIDATES, FINAL_TOP_K);

        List<AnswerCaseObservation> observations = new ArrayList<>();
        long experimentStartNanos = System.nanoTime();

        for (RetrievalEvaluationDataset.EvaluationCase evaluationCase : dataset.cases()) {
            long caseStartNanos = System.nanoTime();

            HybridRetrievalResult hybridResult = retrievalService.retrieveHybrid(
                    evaluationCase.query(),
                    scope,
                    CANDIDATE_TOP_K,
                    NUM_CANDIDATES,
                    FINAL_TOP_K
            );

            RerankedRetrievalResult rankedResult = rerankingService.rerank(evaluationCase.query(), hybridResult, RERANK_ENABLED);
            if (RERANK_ENABLED) {
                assertThat(rankedResult.status()).isEqualTo(RerankStatus.APPLIED);
                assertThat(rankedResult.rerankModel()).isEqualTo("qwen3-rerank");
            } else {
                assertThat(rankedResult.status()).isEqualTo(RerankStatus.DISABLED);
            }

            AssembledContext context = contextAssembler.assemble(rankedResult.rankedChunks(), MAX_CONTEXT_CHARS);
            Set<String> contextChunkIds = context.chunks().stream().map(chunk -> chunk.chunkId()).collect(java.util.stream.Collectors.toSet());

            try {
                RagAnswer answer = answerService.answer(evaluationCase.query(), context);
                long endToEndDurationMs = Duration.ofNanos(System.nanoTime() - caseStartNanos).toMillis();
                List<String> citedChunkIds = answer.citations().stream().map(citation -> citation.chunkId()).toList();
                boolean citationsLegal = contextChunkIds.containsAll(citedChunkIds);
                boolean goldCitationHit = evaluationCase.relevantChunkIds().stream().anyMatch(citedChunkIds::contains);
                boolean answerabilityCorrect = isAnswerabilityCorrect(evaluationCase.expectedAnswerability(), answer.status());

                observations.add(new AnswerCaseObservation(
                        evaluationCase.caseId(),
                        evaluationCase.query(),
                        evaluationCase.queryType(),
                        evaluationCase.expectedAnswerability(),
                        evaluationCase.relevantChunkIds(),
                        contextChunkIds.stream().sorted().toList(),
                        answer.status(),
                        answer.answer(),
                        citedChunkIds,
                        citationsLegal,
                        goldCitationHit,
                        answerabilityCorrect,
                        endToEndDurationMs,
                        null
                ));
            } catch (RagAnswerException e) {
                long endToEndDurationMs = Duration.ofNanos(System.nanoTime() - caseStartNanos).toMillis();
                observations.add(new AnswerCaseObservation(
                        evaluationCase.caseId(),
                        evaluationCase.query(),
                        evaluationCase.queryType(),
                        evaluationCase.expectedAnswerability(),
                        evaluationCase.relevantChunkIds(),
                        contextChunkIds.stream().sorted().toList(),
                        null,
                        null,
                        List.of(),
                        false,
                        false,
                        false,
                        endToEndDurationMs,
                        e.getMessage()
                ));
            }
        }

        long experimentDurationMs = Duration.ofNanos(System.nanoTime() - experimentStartNanos).toMillis();
        AnswerEvaluationSummary summary = summarize(observations);

        assertThat(observations).hasSize(20);
        assertThat(summary.answerableCases()).isEqualTo(16);
        assertThat(summary.unanswerableCases()).isEqualTo(4);

        RagAnswerEvaluationReport report = new RagAnswerEvaluationReport(
                dataset.evaluationSetVersion(),
                manifest.schemaVersion(),
                DocumentCleaner.CLEANING_VERSION,
                chunkingProperties.chunkerVersion(),
                indexProperties.getIndexAlias(),
                RERANK_ENABLED ? "HYBRID_RRF_QWEN3_RERANK" : "HYBRID_RRF",
                RERANK_ENABLED,
                FINAL_TOP_K,
                MAX_CONTEXT_CHARS,
                true,
                experimentDurationMs,
                summary,
                observations
        );

        Files.createDirectories(REPORT_PATH.getParent());
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), report);

        printSummary(summary);
        printBadCases(observations);
        System.out.printf("report=%s, experimentDurationMs=%d%n", REPORT_PATH.toAbsolutePath(), experimentDurationMs);
    }

    private boolean isAnswerabilityCorrect(
            RetrievalEvaluationDataset.ExpectedAnswerability expected,
            RagAnswerStatus actual
    ) {
        if (expected == RetrievalEvaluationDataset.ExpectedAnswerability.ANSWERABLE) return actual == RagAnswerStatus.ANSWERED;
        return actual == RagAnswerStatus.INSUFFICIENT_CONTEXT;
    }

    /** 汇总拒答、引用和端到端延迟指标。 */
    private AnswerEvaluationSummary summarize(List<AnswerCaseObservation> observations) {
        long answerableCases = observations.stream()
                .filter(observation -> observation.expectedAnswerability() == RetrievalEvaluationDataset.ExpectedAnswerability.ANSWERABLE)
                .count();
        long unanswerableCases = observations.size() - answerableCases;
        long generationFailures = observations.stream().filter(observation -> observation.error() != null).count();
        long answeredCases = observations.stream().filter(observation -> observation.actualStatus() == RagAnswerStatus.ANSWERED).count();
        long refusalCases = observations.stream().filter(observation -> observation.actualStatus() == RagAnswerStatus.INSUFFICIENT_CONTEXT).count();
        long answerabilityCorrectCases = observations.stream().filter(AnswerCaseObservation::answerabilityCorrect).count();
        long correctUnanswerableCases = observations.stream()
                .filter(observation -> observation.expectedAnswerability() == RetrievalEvaluationDataset.ExpectedAnswerability.UNANSWERABLE)
                .filter(AnswerCaseObservation::answerabilityCorrect)
                .count();
        long totalCitations = observations.stream().mapToLong(observation -> observation.citedChunkIds().size()).sum();
        long legalCitations = observations.stream()
                .filter(AnswerCaseObservation::citationsLegal)
                .mapToLong(observation -> observation.citedChunkIds().size())
                .sum();
        long goldCitationHitCases = observations.stream()
                .filter(observation -> observation.expectedAnswerability() == RetrievalEvaluationDataset.ExpectedAnswerability.ANSWERABLE)
                .filter(AnswerCaseObservation::goldCitationHit)
                .count();

        List<Long> sortedDurations = observations.stream().map(AnswerCaseObservation::endToEndDurationMs).sorted().toList();

        return new AnswerEvaluationSummary(
                observations.size(),
                answerableCases,
                unanswerableCases,
                generationFailures,
                answeredCases,
                refusalCases,
                answerabilityCorrectCases,
                (double) answerabilityCorrectCases / observations.size(),
                correctUnanswerableCases,
                (double) correctUnanswerableCases / unanswerableCases,
                totalCitations,
                legalCitations,
                totalCitations == 0 ? 0.0 : (double) legalCitations / totalCitations,
                goldCitationHitCases,
                (double) goldCitationHitCases / answerableCases,
                nearestRankPercentile(sortedDurations, 0.50),
                nearestRankPercentile(sortedDurations, 0.95)
        );
    }

    private long nearestRankPercentile(List<Long> sortedValues, double percentile) {
        int rank = (int) Math.ceil(percentile * sortedValues.size());
        return sortedValues.get(Math.max(rank, 1) - 1);
    }

    private void printSummary(AnswerEvaluationSummary summary) {
        System.out.printf(
                "cases=%d, answerable=%d, unanswerable=%d, failures=%d, answered=%d, refusals=%d, answerabilityAccuracy=%.4f, unanswerableAccuracy=%.4f, citationLegalRate=%.4f, goldCitationHitRate=%.4f, endToEndP50Ms=%d, endToEndP95Ms=%d%n",
                summary.totalCases(),
                summary.answerableCases(),
                summary.unanswerableCases(),
                summary.generationFailures(),
                summary.answeredCases(),
                summary.refusalCases(),
                summary.answerabilityAccuracy(),
                summary.unanswerableAccuracy(),
                summary.citationLegalRate(),
                summary.goldCitationHitRate(),
                summary.endToEndLatencyP50Ms(),
                summary.endToEndLatencyP95Ms()
        );
    }

    private void printBadCases(List<AnswerCaseObservation> observations) {
        observations.stream()
                .filter(observation -> !observation.answerabilityCorrect()
                        || (observation.expectedAnswerability() == RetrievalEvaluationDataset.ExpectedAnswerability.ANSWERABLE
                        && !observation.goldCitationHit()))
                .forEach(observation -> System.out.printf(
                        "badCase=%s, type=%s, expected=%s, actual=%s, legal=%s, goldCitationHit=%s, cited=%s, relevant=%s, error=%s, answer=%s%n",
                        observation.caseId(),
                        observation.queryType(),
                        observation.expectedAnswerability(),
                        observation.actualStatus(),
                        observation.citationsLegal(),
                        observation.goldCitationHit(),
                        observation.citedChunkIds(),
                        observation.relevantChunkIds(),
                        observation.error(),
                        preview(observation.answer())
                ));
    }

    private String preview(String answer) {
        if (answer == null) return null;
        String singleLine = answer.replace('\n', ' ');
        return singleLine.length() <= 160 ? singleLine : singleLine.substring(0, 160) + "...";
    }

    private record RagAnswerEvaluationReport(
            String evaluationSetVersion,
            String corpusSchemaVersion,
            String cleaningVersion,
            String chunkerVersion,
            String indexAlias,
            String retrievalStrategy,
            boolean rerankEnabled,
            int finalTopK,
            int maxContextChars,
            boolean retrievalWarmupExcluded,
            long experimentDurationMs,
            AnswerEvaluationSummary summary,
            List<AnswerCaseObservation> cases
    ) {
    }

    private record AnswerEvaluationSummary(
            long totalCases,
            long answerableCases,
            long unanswerableCases,
            long generationFailures,
            long answeredCases,
            long refusalCases,
            long answerabilityCorrectCases,
            double answerabilityAccuracy,
            long correctUnanswerableCases,
            double unanswerableAccuracy,
            long totalCitations,
            long legalCitations,
            double citationLegalRate,
            long goldCitationHitCases,
            double goldCitationHitRate,
            long endToEndLatencyP50Ms,
            long endToEndLatencyP95Ms
    ) {
    }

    private record AnswerCaseObservation(
            String caseId,
            String query,
            RetrievalEvaluationDataset.QueryType queryType,
            RetrievalEvaluationDataset.ExpectedAnswerability expectedAnswerability,
            List<String> relevantChunkIds,
            List<String> contextChunkIds,
            RagAnswerStatus actualStatus,
            String answer,
            List<String> citedChunkIds,
            boolean citationsLegal,
            boolean goldCitationHit,
            boolean answerabilityCorrect,
            long endToEndDurationMs,
            String error
    ) {
    }
}