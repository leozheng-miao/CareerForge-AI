package com.leo.careerforgeai.knowledge.evaluation.dataset;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 定义固定检索评测集及每条人工 Gold Label 的结构和约束
 * @param schemaVersion
 * @param evaluationSetVersion
 * @param corpusManifestResource
 * @param cases
 */
public record RetrievalEvaluationDataset(
        String schemaVersion,
        String evaluationSetVersion,
        String corpusManifestResource,
        List<EvaluationCase> cases
) {

    public static final String SUPPORTED_SCHEMA_VERSION = "rag-retrieval-evaluation-v1";
    public static final String SUPPORTED_EVALUATION_SET_VERSION = "careerforge-rag-eval-v1";
    public static final String EXPECTED_CORPUS_MANIFEST = "rag/evaluation/corpus-manifest.json";
    public static final int MINIMUM_CASE_COUNT = 20;

    public RetrievalEvaluationDataset {
        requireEqual("schemaVersion", SUPPORTED_SCHEMA_VERSION, schemaVersion);
        requireEqual("evaluationSetVersion", SUPPORTED_EVALUATION_SET_VERSION, evaluationSetVersion);
        requireEqual("corpusManifestResource", EXPECTED_CORPUS_MANIFEST, corpusManifestResource);
        if (cases == null || cases.size() < MINIMUM_CASE_COUNT) throw new IllegalArgumentException("评测集至少需要 " + MINIMUM_CASE_COUNT + " 条 Case");
        cases = List.copyOf(cases);

        Set<String> caseIds = new HashSet<>();
        Set<String> queries = new HashSet<>();
        EnumSet<QueryType> queryTypes = EnumSet.noneOf(QueryType.class);
        int unanswerableCount = 0;

        for (EvaluationCase evaluationCase : cases) {
            if (evaluationCase == null) throw new IllegalArgumentException("cases 不能包含空元素");
            if (!caseIds.add(evaluationCase.caseId())) throw new IllegalArgumentException("caseId 重复：" + evaluationCase.caseId());
            if (!queries.add(evaluationCase.query())) throw new IllegalArgumentException("query 重复：" + evaluationCase.query());
            queryTypes.add(evaluationCase.queryType());
            if (evaluationCase.expectedAnswerability() == ExpectedAnswerability.UNANSWERABLE) unanswerableCount++;
        }

        if (!queryTypes.containsAll(EnumSet.allOf(QueryType.class))) throw new IllegalArgumentException("评测集没有覆盖全部 QueryType");
        if (unanswerableCount < 3) throw new IllegalArgumentException("评测集至少需要 3 条 UNANSWERABLE Case");
    }

    public record EvaluationCase(
            String caseId,
            String query,
            QueryType queryType,
            ExpectedAnswerability expectedAnswerability,
            List<String> relevantChunkIds,
            String labelReason
    ) {

        public EvaluationCase {
            if (caseId == null || !caseId.matches("rag-eval-[0-9]{3}")) throw new IllegalArgumentException("caseId 格式不合法");
            requireText(query, "query");
            if (query.length() > 2_000) throw new IllegalArgumentException("query 长度不能超过 2000");
            if (queryType == null) throw new IllegalArgumentException("queryType 不能为空");
            if (expectedAnswerability == null) throw new IllegalArgumentException("expectedAnswerability 不能为空");
            if (relevantChunkIds == null) throw new IllegalArgumentException("relevantChunkIds 不能为空");
            relevantChunkIds = List.copyOf(relevantChunkIds);

            Set<String> uniqueChunkIds = new HashSet<>();
            for (String chunkId : relevantChunkIds) {
                if (chunkId == null || !chunkId.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Relevant Chunk ID 必须是小写 SHA-256");
                if (!uniqueChunkIds.add(chunkId)) throw new IllegalArgumentException("Relevant Chunk ID 不能重复：" + chunkId);
            }

            boolean answerable = expectedAnswerability == ExpectedAnswerability.ANSWERABLE;
            if (answerable != !relevantChunkIds.isEmpty()) throw new IllegalArgumentException("expectedAnswerability 与 relevantChunkIds 不一致");
            requireText(labelReason, "labelReason");
        }
    }

    public enum QueryType {
        DIRECT,
        PARAPHRASE,
        TECHNICAL_TERM,
        MULTI_CHUNK,
        CROSS_DOCUMENT,
        UNANSWERABLE
    }

    public enum ExpectedAnswerability {
        ANSWERABLE,
        UNANSWERABLE
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " 不能为空");
    }

    private static void requireEqual(String fieldName, String expected, String actual) {
        if (!expected.equals(actual)) throw new IllegalArgumentException(fieldName + " 不受支持，expected=" + expected + ", actual=" + actual);
    }
}