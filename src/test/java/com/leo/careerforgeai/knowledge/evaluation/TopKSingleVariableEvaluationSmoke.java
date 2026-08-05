package com.leo.careerforgeai.knowledge.evaluation;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.leo.careerforgeai.knowledge.application.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import com.leo.careerforgeai.knowledge.infrastructure.document.ChunkingProperties;
import com.leo.careerforgeai.knowledge.infrastructure.document.DocumentCleaner;
import com.leo.careerforgeai.knowledge.infrastructure.document.KnowledgeSourceProperties;
import com.leo.careerforgeai.knowledge.infrastructure.document.MarkdownChunker;
import com.leo.careerforgeai.knowledge.infrastructure.document.MarkdownDocumentLoader;
import com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.KnowledgeIndexProperties;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "careerforge.model.base-url=https://example.invalid",
        "careerforge.model.api-key=smoke-test-key",
        "careerforge.model.name=smoke-test-model"
})
class TopKSingleVariableEvaluationSmoke {

    private static final int METRIC_TOP_K = 5;
    private static final List<Integer> CANDIDATE_TOP_K_VALUES = List.of(5, 10, 20);
    private static final int NUM_CANDIDATES = 50;
    private static final Path REPORT_PATH = Path.of("target/rag-evaluation/top-k-experiment.json");

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private MarkdownDocumentLoader documentLoader;

    @Autowired
    private DocumentCleaner documentCleaner;

    @Autowired
    private MarkdownChunker markdownChunker;

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

    /** 固定其余检索参数，对比不同候选 Top K 下的 RRF Top 5 效果。 */
    @Test
    void shouldCompareCandidateTopKAsSingleVariable() throws IOException {
        assertThat(elasticsearchClient.indices().existsAlias(request -> request.name(indexProperties.getIndexAlias())).value())
                .as("评测前必须存在已发布的知识库 Alias")
                .isTrue();

        EvaluationCorpusGuard corpusGuard = new EvaluationCorpusGuard(jsonMapper);
        EvaluationCorpusManifest manifest = corpusGuard.loadManifest();
        RetrievalEvaluationDataset dataset = new RetrievalEvaluationDatasetLoader(jsonMapper).load();
        List<SourceDocument> sourceDocuments = documentLoader.loadAll();

        corpusGuard.verify(manifest, sourceDocuments, DocumentCleaner.CLEANING_VERSION, chunkingProperties.chunkerVersion());
        verifyRelevantChunkIdsStillExist(dataset, sourceDocuments);

        RetrievalScope scope = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of());
        for (int candidateTopK : CANDIDATE_TOP_K_VALUES) {
            retrievalService.retrieveHybrid("固定评测预热查询", scope, candidateTopK, NUM_CANDIDATES, METRIC_TOP_K);
        }

        RetrievalMetricsCalculator calculator = new RetrievalMetricsCalculator();
        RetrievalMetricsAggregator aggregator = new RetrievalMetricsAggregator();
        List<TopKExperimentResult> experimentResults = new ArrayList<>();
        long evaluationStartNanos = System.nanoTime();

        for (int candidateTopK : CANDIDATE_TOP_K_VALUES) {
            List<RetrievalCaseMeasurement> measurements = new ArrayList<>();
            List<TopKCaseResult> caseResults = new ArrayList<>();

            for (RetrievalEvaluationDataset.EvaluationCase evaluationCase : dataset.cases()) {
                if (evaluationCase.expectedAnswerability() == RetrievalEvaluationDataset.ExpectedAnswerability.UNANSWERABLE) continue;

                long retrievalStartNanos = System.nanoTime();
                HybridRetrievalResult result = retrievalService.retrieveHybrid(
                        evaluationCase.query(),
                        scope,
                        candidateTopK,
                        NUM_CANDIDATES,
                        METRIC_TOP_K
                );
                long durationMs = Duration.ofNanos(System.nanoTime() - retrievalStartNanos).toMillis();

                List<String> retrievedChunkIds = result.rrfChunks().stream()
                        .map(RrfRankedChunk::chunk)
                        .map(DocumentChunk::chunkId)
                        .limit(METRIC_TOP_K)
                        .toList();

                RetrievalCaseMetrics metrics = calculator.calculate(
                        evaluationCase.caseId(),
                        Set.copyOf(evaluationCase.relevantChunkIds()),
                        retrievedChunkIds,
                        METRIC_TOP_K
                );
                RetrievalCaseMeasurement measurement = new RetrievalCaseMeasurement(metrics, durationMs);
                measurements.add(measurement);
                caseResults.add(new TopKCaseResult(
                        evaluationCase.caseId(),
                        evaluationCase.query(),
                        evaluationCase.queryType(),
                        evaluationCase.relevantChunkIds(),
                        retrievedChunkIds,
                        measurement
                ));
            }

            RetrievalEvaluationSummary summary = aggregator.aggregate(RetrievalStrategy.HYBRID_RRF, measurements);
            assertThat(caseResults).hasSize(16);
            assertThat(summary.evaluatedCases()).isEqualTo(16);
            assertThat(summary.topK()).isEqualTo(METRIC_TOP_K);
            experimentResults.add(new TopKExperimentResult(candidateTopK, summary, caseResults));
        }

        long evaluationDurationMs = Duration.ofNanos(System.nanoTime() - evaluationStartNanos).toMillis();
        TopKExperimentReport report = new TopKExperimentReport(
                dataset.evaluationSetVersion(),
                manifest.schemaVersion(),
                DocumentCleaner.CLEANING_VERSION,
                chunkingProperties.chunkerVersion(),
                indexProperties.getIndexAlias(),
                METRIC_TOP_K,
                NUM_CANDIDATES,
                CANDIDATE_TOP_K_VALUES,
                false,
                true,
                evaluationDurationMs,
                experimentResults
        );

        Files.createDirectories(REPORT_PATH.getParent());
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), report);

        experimentResults.forEach(this::printResult);
        printBadCases(experimentResults);
        System.out.printf("report=%s, variable=candidateTopK, fixedMetricTopK=%d, fixedNumCandidates=%d, evaluationDurationMs=%d%n",
                REPORT_PATH.toAbsolutePath(), METRIC_TOP_K, NUM_CANDIDATES, evaluationDurationMs);
    }

    /** 确认固定标注仍能映射到当前确定性切分结果。 */
    private void verifyRelevantChunkIdsStillExist(RetrievalEvaluationDataset dataset, List<SourceDocument> sourceDocuments) {
        Set<String> currentChunkIds = sourceDocuments.stream()
                .map(documentCleaner::clean)
                .flatMap(cleanedDocument -> markdownChunker.chunk(cleanedDocument).stream())
                .map(DocumentChunk::chunkId)
                .collect(Collectors.toSet());

        Set<String> labeledChunkIds = dataset.cases().stream()
                .flatMap(evaluationCase -> evaluationCase.relevantChunkIds().stream())
                .collect(Collectors.toSet());

        assertThat(currentChunkIds).hasSize(43);
        assertThat(currentChunkIds).containsAll(labeledChunkIds);
    }

    private void printResult(TopKExperimentResult result) {
        RetrievalEvaluationSummary summary = result.summary();
        System.out.printf(
                "candidateTopK=%d, cases=%d, Recall@%d=%.4f, Precision@%d=%.4f, MRR=%.4f, p50Ms=%d, p95Ms=%d%n",
                result.candidateTopK(),
                summary.evaluatedCases(),
                summary.topK(),
                summary.meanRecallAtK(),
                summary.topK(),
                summary.meanPrecisionAtK(),
                summary.mrr(),
                summary.retrievalLatencyP50Ms(),
                summary.retrievalLatencyP95Ms()
        );
    }

    /** 输出每个参数组未达到完整 Recall 的真实 Bad Case。 */
    private void printBadCases(List<TopKExperimentResult> results) {
        results.forEach(result -> result.cases().stream()
                .filter(caseResult -> caseResult.measurement().metrics().recallAtK() < 1.0)
                .forEach(caseResult -> System.out.printf(
                        "candidateTopK=%d, badCase=%s, type=%s, recall=%.4f, relevant=%s, retrieved=%s%n",
                        result.candidateTopK(),
                        caseResult.caseId(),
                        caseResult.queryType(),
                        caseResult.measurement().metrics().recallAtK(),
                        caseResult.relevantChunkIds(),
                        caseResult.retrievedChunkIds()
                )));
    }

    private record TopKExperimentReport(
            String evaluationSetVersion,
            String corpusSchemaVersion,
            String cleaningVersion,
            String chunkerVersion,
            String indexAlias,
            int metricTopK,
            int numCandidates,
            List<Integer> candidateTopKValues,
            boolean rerankEnabled,
            boolean warmupExcluded,
            long evaluationDurationMs,
            List<TopKExperimentResult> experiments
    ) {
    }

    private record TopKExperimentResult(
            int candidateTopK,
            RetrievalEvaluationSummary summary,
            List<TopKCaseResult> cases
    ) {
    }

    private record TopKCaseResult(
            String caseId,
            String query,
            RetrievalEvaluationDataset.QueryType queryType,
            List<String> relevantChunkIds,
            List<String> retrievedChunkIds,
            RetrievalCaseMeasurement measurement
    ) {
    }
}