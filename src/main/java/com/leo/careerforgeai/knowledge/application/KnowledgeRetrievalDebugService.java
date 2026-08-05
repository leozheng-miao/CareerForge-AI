package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用正式检索参数执行 BM25、向量检索和 RRF，并返回可观察的调试结果
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
@Service
@Slf4j
public class KnowledgeRetrievalDebugService {

    private static final int MAX_QUERY_CHARS = 2_000;

    private final KnowledgeRetrievalService retrievalService;
    private final RagQueryProperties properties;

    public KnowledgeRetrievalDebugService(KnowledgeRetrievalService retrievalService, RagQueryProperties properties) {
        this.retrievalService = retrievalService;
        this.properties = properties;
    }

    /** 执行不包含 Rerank 和答案生成的真实混合检索调试。 */
    public RetrievalDebugResult debug(String query, RetrievalScope scope) {
        validateInput(query, scope);

        String requestId = UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        log.info("检索调试开始，requestId={}, knowledgeBaseId={}", requestId, scope.knowledgeBaseId());

        try {
            HybridRetrievalResult retrievalResult = retrievalService.retrieveHybrid(
                    query,
                    scope,
                    properties.getCandidateTopK(),
                    properties.getNumCandidates(),
                    properties.getFinalTopK()
            );
            long totalDurationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            RetrievalDebugResult result = new RetrievalDebugResult(requestId, retrievalResult, totalDurationMs);
            log.info("检索调试完成，requestId={}, knowledgeBaseId={}, bm25Hits={}, vectorHits={}, rrfHits={}, totalDurationMs={}", requestId, scope.knowledgeBaseId(), retrievalResult.comparisonResult().bm25Result().chunks().size(), retrievalResult.comparisonResult().vectorResult().chunks().size(), retrievalResult.rrfChunks().size(), totalDurationMs);
            return result;
        } catch (RuntimeException e) {
            long totalDurationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.error("检索调试失败，requestId={}, knowledgeBaseId={}, errorType={}, error={}, totalDurationMs={}", requestId, scope.knowledgeBaseId(), e.getClass().getSimpleName(), e.getMessage(), totalDurationMs);
            throw e;
        }
    }

    private void validateInput(String query, RetrievalScope scope) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        if (query.length() > MAX_QUERY_CHARS) throw new IllegalArgumentException("query 长度不能超过 " + MAX_QUERY_CHARS);
        if (scope == null) throw new IllegalArgumentException("scope 不能为空");
    }
}