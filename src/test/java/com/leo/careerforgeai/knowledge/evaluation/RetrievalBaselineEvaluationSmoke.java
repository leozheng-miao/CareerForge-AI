package com.leo.careerforgeai.knowledge.evaluation;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.leo.careerforgeai.knowledge.application.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "careerforge.model.base-url=https://example.invalid",
        "careerforge.model.api-key=smoke-test-key",
        "careerforge.model.name=smoke-test-model"
})
class RetrievalBaselineEvaluationSmoke {

    private static final int METRIC_TOP_K = 5;
    private static final int CANDIDATE_TOP_K = 10;
    private static final int NUM_CANDIDATES = 50;
    private static final Path REPORT_PATH = Path.of("target/rag-evaluation/retrieval-baseline.json");

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

    /** 使用固定 Gold Label 运行真实 BM25、Vector 和 RRF 基线评测。 */
    @Test
    void shouldEvaluateRealRetrievalBaseline() throws IOException {
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
        retrievalService.retrieveHybrid("固定评测预热查询", scope, CANDIDATE_TOP_K, NUM_CANDIDATES, METRIC_TOP_K);

        RetrievalMetricsCalculator calculator = new RetrievalMetricsCalculator();
        RetrievalMetricsAggregator aggregator = new RetrievalMetricsAggregator();
        Map<RetrievalStrategy, List<RetrievalCaseMeasurement>> measurements = createMeasurementMap();
        List<CaseEvaluationResult> caseResults = new ArrayList<>();

        long evaluationStartNanos = System.nanoTime();

        for (RetrievalEvaluationDataset.EvaluationCase evaluationCase : dataset.cases()) {
            if (evaluationCase.expectedAnswerability() == RetrievalEvaluationDataset.ExpectedAnswerability.UNANSWERABLE) continue;

            long hybridStartNanos = System.nanoTime();
            HybridRetrievalResult hybridResult = retrievalService.retrieveHybrid(
                    evaluationCase.query(),
                    scope,
                    CANDIDATE_TOP_K,
                    NUM_CANDIDATES,
                    METRIC_TOP_K
            );
            long hybridWallDurationMs = Duration.ofNanos(System.nanoTime() - hybridStartNanos).toMillis();

            RetrievalComparisonResult comparison = hybridResult.comparisonResult();
            List<StrategyCaseResult> strategyResults = List.of(
                    evaluateStrategy(evaluationCase, RetrievalStrategy.BM25, ids(comparison.bm25Result()), comparison.bm25Result().durationMs(), calculator, measurements),
                    evaluateStrategy(evaluationCase, RetrievalStrategy.VECTOR, ids(comparison.vectorResult()), comparison.vectorTotalDurationMs(), calculator, measurements),
                    evaluateStrategy(evaluationCase, RetrievalStrategy.HYBRID_RRF, rrfIds(hybridResult.rrfChunks()), hybridWallDurationMs, calculator, measurements)
            );

            caseResults.add(new CaseEvaluationResult(
                    evaluationCase.caseId(),
                    evaluationCase.query(),
                    evaluationCase.queryType(),
                    evaluationCase.relevantChunkIds(),
                    strategyResults
            ));
        }

        long evaluationDurationMs = Duration.ofNanos(System.nanoTime() - evaluationStartNanos).toMillis();
        List<RetrievalEvaluationSummary> summaries = List.of(
                aggregator.aggregate(RetrievalStrategy.BM25, measurements.get(RetrievalStrategy.BM25)),
                aggregator.aggregate(RetrievalStrategy.VECTOR, measurements.get(RetrievalStrategy.VECTOR)),
                aggregator.aggregate(RetrievalStrategy.HYBRID_RRF, measurements.get(RetrievalStrategy.HYBRID_RRF))
        );

        assertThat(caseResults).hasSize(16);
        assertThat(summaries).allSatisfy(summary -> {
            assertThat(summary.evaluatedCases()).isEqualTo(16);
            assertThat(summary.topK()).isEqualTo(METRIC_TOP_K);
        });

        RetrievalBaselineReport report = new RetrievalBaselineReport(
                dataset.evaluationSetVersion(),
                manifest.schemaVersion(),
                DocumentCleaner.CLEANING_VERSION,
                chunkingProperties.chunkerVersion(),
                indexProperties.getIndexAlias(),
                METRIC_TOP_K,
                CANDIDATE_TOP_K,
                NUM_CANDIDATES,
                true,
                evaluationDurationMs,
                summaries,
                caseResults
        );

        Files.createDirectories(REPORT_PATH.getParent());
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), report);

        summaries.forEach(this::printSummary);
        printBadCases(caseResults);
        System.out.printf("report=%s, answerableCases=%d, unanswerableCases=%d, evaluationDurationMs=%d%n",
                REPORT_PATH.toAbsolutePath(), caseResults.size(), dataset.cases().size() - caseResults.size(), evaluationDurationMs);
    }

    /** 确认所有 Gold Label 仍能映射到当前确定性切分结果。 */
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

    /** 计算并保存某一种策略在当前 Case 上的指标。 */
    private StrategyCaseResult evaluateStrategy(
            RetrievalEvaluationDataset.EvaluationCase evaluationCase,
            RetrievalStrategy strategy,
            List<String> rankedChunkIds,
            long durationMs,
            RetrievalMetricsCalculator calculator,
            Map<RetrievalStrategy, List<RetrievalCaseMeasurement>> measurements
    ) {
        List<String> rankedAtK = rankedChunkIds.stream().limit(METRIC_TOP_K).toList();
        RetrievalCaseMetrics metrics = calculator.calculate(
                evaluationCase.caseId(),
                Set.copyOf(evaluationCase.relevantChunkIds()),
                rankedAtK,
                METRIC_TOP_K
        );
        RetrievalCaseMeasurement measurement = new RetrievalCaseMeasurement(metrics, durationMs);
        measurements.get(strategy).add(measurement);
        return new StrategyCaseResult(strategy, rankedAtK, measurement);
    }

    private Map<RetrievalStrategy, List<RetrievalCaseMeasurement>> createMeasurementMap() {
        Map<RetrievalStrategy, List<RetrievalCaseMeasurement>> measurements = new EnumMap<>(RetrievalStrategy.class);
        measurements.put(RetrievalStrategy.BM25, new ArrayList<>());
        measurements.put(RetrievalStrategy.VECTOR, new ArrayList<>());
        measurements.put(RetrievalStrategy.HYBRID_RRF, new ArrayList<>());
        return measurements;
    }

    private List<String> ids(RetrievalResult result) {
        return result.chunks().stream().map(chunk -> chunk.chunk().chunkId()).toList();
    }

    private List<String> rrfIds(List<RrfRankedChunk> chunks) {
        return chunks.stream().map(chunk -> chunk.chunk().chunkId()).toList();
    }

    private void printSummary(RetrievalEvaluationSummary summary) {
        System.out.printf(
                "strategy=%s, cases=%d, Recall@%d=%.4f, Precision@%d=%.4f, MRR=%.4f, p50Ms=%d, p95Ms=%d%n",
                summary.strategy(),
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

    /** 输出未达到完整 Recall 的 Case，作为真实 Bad Case 候选。 */
    private void printBadCases(List<CaseEvaluationResult> caseResults) {
        caseResults.forEach(caseResult -> caseResult.strategies().stream()
                .filter(strategyResult -> strategyResult.measurement().metrics().recallAtK() < 1.0)
                .forEach(strategyResult -> System.out.printf(
                        "badCase=%s, type=%s, strategy=%s, recall=%.4f, firstRelevantRank=%d, relevant=%s, retrieved=%s%n",
                        caseResult.caseId(),
                        caseResult.queryType(),
                        strategyResult.strategy(),
                        strategyResult.measurement().metrics().recallAtK(),
                        strategyResult.measurement().metrics().firstRelevantRank(),
                        caseResult.relevantChunkIds(),
                        strategyResult.rankedChunkIds()
                )));
    }

    private record RetrievalBaselineReport(
            String evaluationSetVersion,
            String corpusSchemaVersion,
            String cleaningVersion,
            String chunkerVersion,
            String indexAlias,
            int metricTopK,
            int candidateTopK,
            int numCandidates,
            boolean warmupExcluded,
            long evaluationDurationMs,
            List<RetrievalEvaluationSummary> summaries,
            List<CaseEvaluationResult> cases
    ) {
    }

    private record CaseEvaluationResult(
            String caseId,
            String query,
            RetrievalEvaluationDataset.QueryType queryType,
            List<String> relevantChunkIds,
            List<StrategyCaseResult> strategies
    ) {
    }

    private record StrategyCaseResult(
            RetrievalStrategy strategy,
            List<String> rankedChunkIds,
            RetrievalCaseMeasurement measurement
    ) {
    }
}