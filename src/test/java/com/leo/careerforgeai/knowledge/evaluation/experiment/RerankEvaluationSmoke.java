package com.leo.careerforgeai.knowledge.evaluation.experiment;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.leo.careerforgeai.knowledge.application.rerank.KnowledgeRerankingService;
import com.leo.careerforgeai.knowledge.application.retrieval.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankStatus;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.config.ChunkingProperties;
import com.leo.careerforgeai.knowledge.evaluation.dataset.EvaluationCorpusGuard;
import com.leo.careerforgeai.knowledge.evaluation.dataset.EvaluationCorpusManifest;
import com.leo.careerforgeai.knowledge.evaluation.metrics.RetrievalCaseMeasurement;
import com.leo.careerforgeai.knowledge.evaluation.metrics.RetrievalCaseMetrics;
import com.leo.careerforgeai.knowledge.evaluation.dataset.RetrievalEvaluationDataset;
import com.leo.careerforgeai.knowledge.evaluation.dataset.RetrievalEvaluationDatasetLoader;
import com.leo.careerforgeai.knowledge.evaluation.metrics.RetrievalEvaluationSummary;
import com.leo.careerforgeai.knowledge.evaluation.metrics.RetrievalMetricsAggregator;
import com.leo.careerforgeai.knowledge.evaluation.metrics.RetrievalMetricsCalculator;
import com.leo.careerforgeai.knowledge.evaluation.metrics.RetrievalStrategy;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RerankEvaluationSmoke {

    private static final int METRIC_TOP_K = 5;
    private static final int RRF_CANDIDATE_TOP_K = 10;
    private static final int ROUTE_CANDIDATE_TOP_K = 10;
    private static final int NUM_CANDIDATES = 50;
    private static final Path REPORT_PATH = Path.of("target/rag-evaluation/rerank-evaluation.json");

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private KnowledgeRerankingService rerankingService;

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

    /** 使用固定评测集比较同一批 RRF 候选重排前后的指标、延迟和 Token。 */
    @Test
    void shouldEvaluateRealLlmRerank() throws IOException {
        assertThat(elasticsearchClient.indices().existsAlias(request -> request.name(indexProperties.getIndexAlias())).value())
                .as("评测前必须存在已发布的知识库 Alias")
                .isTrue();

        EvaluationCorpusGuard corpusGuard = new EvaluationCorpusGuard(jsonMapper);
        EvaluationCorpusManifest manifest = corpusGuard.loadManifest();
        RetrievalEvaluationDataset dataset = new RetrievalEvaluationDatasetLoader(jsonMapper).load();
        List<SourceDocument> sourceDocuments = documentLoader.loadAll();

        corpusGuard.verify(manifest, sourceDocuments, DocumentCleaner.CLEANING_VERSION, chunkingProperties.chunkerVersion());

        RetrievalScope scope = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of());
        retrievalService.retrieveHybrid("Rerank 固定评测预热查询", scope, ROUTE_CANDIDATE_TOP_K, NUM_CANDIDATES, RRF_CANDIDATE_TOP_K);

        RetrievalMetricsCalculator calculator = new RetrievalMetricsCalculator();
        RetrievalMetricsAggregator aggregator = new RetrievalMetricsAggregator();
        List<RetrievalCaseMeasurement> rrfMeasurements = new ArrayList<>();
        List<RetrievalCaseMeasurement> rerankEndToEndMeasurements = new ArrayList<>();
        List<RetrievalCaseMeasurement> rerankOnlyMeasurements = new ArrayList<>();
        List<RerankCaseResult> caseResults = new ArrayList<>();

        long experimentStartNanos = System.nanoTime();

        for (RetrievalEvaluationDataset.EvaluationCase evaluationCase : dataset.cases()) {
            if (evaluationCase.expectedAnswerability() == RetrievalEvaluationDataset.ExpectedAnswerability.UNANSWERABLE) continue;

            long retrievalStartNanos = System.nanoTime();
            HybridRetrievalResult hybridResult = retrievalService.retrieveHybrid(
                    evaluationCase.query(),
                    scope,
                    ROUTE_CANDIDATE_TOP_K,
                    NUM_CANDIDATES,
                    RRF_CANDIDATE_TOP_K
            );
            long retrievalDurationMs = Duration.ofNanos(System.nanoTime() - retrievalStartNanos).toMillis();

            assertThat(hybridResult.rrfChunks()).hasSize(RRF_CANDIDATE_TOP_K);

            long endToEndStartNanos = System.nanoTime();
            RerankedRetrievalResult rerankedResult = rerankingService.rerank(evaluationCase.query(), hybridResult, true);
            long rerankCallWallDurationMs = Duration.ofNanos(System.nanoTime() - endToEndStartNanos).toMillis();
            long endToEndDurationMs = retrievalDurationMs + rerankCallWallDurationMs;

            assertThat(rerankedResult.status()).isIn(RerankStatus.APPLIED, RerankStatus.FALLBACK);
            assertThat(rerankedResult.rankedChunks()).hasSameSizeAs(hybridResult.rrfChunks());

            List<String> rrfTopFive = hybridResult.rrfChunks().stream()
                    .limit(METRIC_TOP_K)
                    .map(candidate -> candidate.chunk().chunkId())
                    .toList();

            List<String> rerankTopFive = rerankedResult.rankedChunks().stream()
                    .limit(METRIC_TOP_K)
                    .map(candidate -> candidate.chunk().chunkId())
                    .toList();

            Set<String> relevantChunkIds = Set.copyOf(evaluationCase.relevantChunkIds());
            RetrievalCaseMetrics rrfMetrics = calculator.calculate(evaluationCase.caseId(), relevantChunkIds, rrfTopFive, METRIC_TOP_K);
            RetrievalCaseMetrics rerankMetrics = calculator.calculate(evaluationCase.caseId(), relevantChunkIds, rerankTopFive, METRIC_TOP_K);

            rrfMeasurements.add(new RetrievalCaseMeasurement(rrfMetrics, retrievalDurationMs));
            rerankEndToEndMeasurements.add(new RetrievalCaseMeasurement(rerankMetrics, endToEndDurationMs));
            rerankOnlyMeasurements.add(new RetrievalCaseMeasurement(rerankMetrics, rerankedResult.rerankDurationMs()));

            caseResults.add(new RerankCaseResult(
                    evaluationCase.caseId(),
                    evaluationCase.query(),
                    evaluationCase.queryType(),
                    evaluationCase.relevantChunkIds(),
                    rrfTopFive,
                    rerankTopFive,
                    rrfMetrics,
                    rerankMetrics,
                    rerankedResult.status(),
                    rerankedResult.rerankModel(),
                    rerankedResult.rerankDurationMs(),
                    rerankedResult.rerankInputTokens(),
                    rerankedResult.rerankOutputTokens(),
                    rerankedResult.rerankTotalTokens()
            ));
        }

        long experimentDurationMs = Duration.ofNanos(System.nanoTime() - experimentStartNanos).toMillis();
        RetrievalEvaluationSummary beforeRerank = aggregator.aggregate(RetrievalStrategy.HYBRID_RRF, rrfMeasurements);
        RetrievalEvaluationSummary afterRerank = aggregator.aggregate(RetrievalStrategy.HYBRID_RRF_RERANK, rerankEndToEndMeasurements);
        RetrievalEvaluationSummary rerankOnlyLatency = aggregator.aggregate(RetrievalStrategy.HYBRID_RRF_RERANK, rerankOnlyMeasurements);

        Map<RerankStatus, Long> statusCounts = caseResults.stream()
                .collect(Collectors.groupingBy(RerankCaseResult::status, Collectors.counting()));
        Set<String> models = caseResults.stream()
                .map(RerankCaseResult::model)
                .filter(model -> model != null && !model.isBlank())
                .collect(Collectors.toSet());

        long totalInputTokens = caseResults.stream().mapToLong(RerankCaseResult::inputTokens).sum();
        long totalOutputTokens = caseResults.stream().mapToLong(RerankCaseResult::outputTokens).sum();
        long totalTokens = caseResults.stream().mapToLong(RerankCaseResult::totalTokens).sum();
        long appliedCount = statusCounts.getOrDefault(RerankStatus.APPLIED, 0L);

        assertThat(caseResults).hasSize(16);
        assertThat(totalTokens).isEqualTo(totalInputTokens + totalOutputTokens);

        RerankExperimentSummary rerankSummary = new RerankExperimentSummary(
                afterRerank,
                rerankOnlyLatency.retrievalLatencyP50Ms(),
                rerankOnlyLatency.retrievalLatencyP95Ms(),
                statusCounts,
                models,
                totalInputTokens,
                totalOutputTokens,
                totalTokens,
                appliedCount == 0 ? 0.0 : (double) totalTokens / appliedCount
        );

        RerankEvaluationReport report = new RerankEvaluationReport(
                dataset.evaluationSetVersion(),
                manifest.schemaVersion(),
                DocumentCleaner.CLEANING_VERSION,
                chunkingProperties.chunkerVersion(),
                indexProperties.getIndexAlias(),
                METRIC_TOP_K,
                RRF_CANDIDATE_TOP_K,
                NUM_CANDIDATES,
                true,
                experimentDurationMs,
                beforeRerank,
                rerankSummary,
                caseResults
        );

        Files.createDirectories(REPORT_PATH.getParent());
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), report);

        printComparison(beforeRerank, rerankSummary);
        printChangedCases(caseResults);
        System.out.printf("report=%s, experimentDurationMs=%d%n", REPORT_PATH.toAbsolutePath(), experimentDurationMs);
    }

    private void printComparison(RetrievalEvaluationSummary before, RerankExperimentSummary after) {
        RetrievalEvaluationSummary reranked = after.qualityAndEndToEndLatency();

        System.out.printf(
                "before=HYBRID_RRF, Recall@%d=%.4f, Precision@%d=%.4f, MRR=%.4f, p50Ms=%d, p95Ms=%d%n",
                before.topK(), before.meanRecallAtK(), before.topK(), before.meanPrecisionAtK(), before.mrr(),
                before.retrievalLatencyP50Ms(), before.retrievalLatencyP95Ms()
        );
        System.out.printf(
                "after=HYBRID_RRF_RERANK, Recall@%d=%.4f, Precision@%d=%.4f, MRR=%.4f, endToEndP50Ms=%d, endToEndP95Ms=%d, rerankP50Ms=%d, rerankP95Ms=%d%n",
                reranked.topK(), reranked.meanRecallAtK(), reranked.topK(), reranked.meanPrecisionAtK(), reranked.mrr(),
                reranked.retrievalLatencyP50Ms(), reranked.retrievalLatencyP95Ms(),
                after.rerankLatencyP50Ms(), after.rerankLatencyP95Ms()
        );
        System.out.printf(
                "deltaRecall=%.4f, deltaPrecision=%.4f, deltaMRR=%.4f, statusCounts=%s, models=%s, inputTokens=%d, outputTokens=%d, totalTokens=%d, avgTokensPerAppliedCall=%.2f%n",
                reranked.meanRecallAtK() - before.meanRecallAtK(),
                reranked.meanPrecisionAtK() - before.meanPrecisionAtK(),
                reranked.mrr() - before.mrr(),
                after.statusCounts(),
                after.models(),
                after.totalInputTokens(),
                after.totalOutputTokens(),
                after.totalTokens(),
                after.averageTokensPerAppliedCall()
        );
    }

    /** 输出 Rerank 改变 Top 5 或改变指标的真实 Case。 */
    private void printChangedCases(List<RerankCaseResult> caseResults) {
        caseResults.stream()
                .filter(result -> !result.rrfTopFive().equals(result.rerankTopFive()))
                .forEach(result -> System.out.printf(
                        "changedCase=%s, type=%s, status=%s, beforeRecall=%.4f, afterRecall=%.4f, beforeFirstRank=%d, afterFirstRank=%d, rrf=%s, rerank=%s%n",
                        result.caseId(),
                        result.queryType(),
                        result.status(),
                        result.rrfMetrics().recallAtK(),
                        result.rerankMetrics().recallAtK(),
                        result.rrfMetrics().firstRelevantRank(),
                        result.rerankMetrics().firstRelevantRank(),
                        result.rrfTopFive(),
                        result.rerankTopFive()
                ));
    }

    private record RerankEvaluationReport(
            String evaluationSetVersion,
            String corpusSchemaVersion,
            String cleaningVersion,
            String chunkerVersion,
            String indexAlias,
            int metricTopK,
            int rerankCandidateTopK,
            int numCandidates,
            boolean retrievalWarmupExcluded,
            long experimentDurationMs,
            RetrievalEvaluationSummary beforeRerank,
            RerankExperimentSummary afterRerank,
            List<RerankCaseResult> cases
    ) {
    }

    private record RerankExperimentSummary(
            RetrievalEvaluationSummary qualityAndEndToEndLatency,
            long rerankLatencyP50Ms,
            long rerankLatencyP95Ms,
            Map<RerankStatus, Long> statusCounts,
            Set<String> models,
            long totalInputTokens,
            long totalOutputTokens,
            long totalTokens,
            double averageTokensPerAppliedCall
    ) {
    }

    private record RerankCaseResult(
            String caseId,
            String query,
            RetrievalEvaluationDataset.QueryType queryType,
            List<String> relevantChunkIds,
            List<String> rrfTopFive,
            List<String> rerankTopFive,
            RetrievalCaseMetrics rrfMetrics,
            RetrievalCaseMetrics rerankMetrics,
            RerankStatus status,
            String model,
            long rerankDurationMs,
            long inputTokens,
            long outputTokens,
            long totalTokens
    ) {
    }
}