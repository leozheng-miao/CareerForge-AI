package com.leo.careerforgeai.knowledge.application.evidence;

import com.leo.careerforgeai.knowledge.application.context.ContextAssembler;
import com.leo.careerforgeai.knowledge.application.rerank.KnowledgeRerankingService;
import com.leo.careerforgeai.knowledge.application.retrieval.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.config.RagQueryProperties;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 编排混合检索、可选重排和上下文组装，返回可追溯证据而不生成最终回答。
 * @author: Miao Zheng
 * @date: 2026-08-06 19:20
 **/
@Service
@Slf4j
public class KnowledgeEvidenceSearchService {

    private static final int MAX_QUERY_CHARS = 2_000;

    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeRerankingService rerankingService;
    private final ContextAssembler contextAssembler;
    private final RagQueryProperties properties;

    public KnowledgeEvidenceSearchService(
            KnowledgeRetrievalService retrievalService,
            KnowledgeRerankingService rerankingService,
            ContextAssembler contextAssembler,
            RagQueryProperties properties
    ) {
        this.retrievalService = retrievalService;
        this.rerankingService = rerankingService;
        this.contextAssembler = contextAssembler;
        this.properties = properties;
    }

    /** 使用自动生成的requestId执行证据搜索。 */
    public KnowledgeEvidenceSearchResult search(String query, RetrievalScope scope) {
        return search(UUID.randomUUID().toString(), query, scope);
    }

    /** 使用调用方提供的可信requestId执行证据搜索。 */
    public KnowledgeEvidenceSearchResult search(
            String requestId,
            String query,
            RetrievalScope scope
    ) {
        validateRequestId(requestId);
        validateInput(query, scope);

        long startNanos = System.nanoTime();
        log.info("职业材料证据搜索开始，requestId={}, knowledgeBaseId={}", requestId, scope.knowledgeBaseId());

        try {
            HybridRetrievalResult retrievalResult = retrievalService.retrieveHybrid(
                    query,
                    scope,
                    properties.getCandidateTopK(),
                    properties.getNumCandidates(),
                    properties.getFinalTopK()
            );
            RerankedRetrievalResult rerankedResult = rerankingService.rerank(
                    query, retrievalResult, properties.isRerankEnabled());
            AssembledContext context = contextAssembler.assemble(
                    rerankedResult.rankedChunks(), properties.getMaxContentChars());
            long totalDurationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            log.info("职业材料证据搜索完成，requestId={}, knowledgeBaseId={}, candidates={}, rerankStatus={}, evidence={}, usedContentChars={}, totalDurationMs={}", requestId, scope.knowledgeBaseId(), retrievalResult.rrfChunks().size(), rerankedResult.status(), context.chunks().size(), context.usedContentChars(), totalDurationMs);
            return new KnowledgeEvidenceSearchResult(
                    requestId, retrievalResult, rerankedResult, context, totalDurationMs);
        } catch (RuntimeException exception) {
            long totalDurationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.error("职业材料证据搜索失败，requestId={}, knowledgeBaseId={}, exceptionType={}, totalDurationMs={}", requestId, scope.knowledgeBaseId(), exception.getClass().getSimpleName(), totalDurationMs);
            throw exception;
        }
    }

    /** 在调用检索链路前校验查询和服务端权限范围。 */
    private void validateInput(String query, RetrievalScope scope) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        if (query.length() > MAX_QUERY_CHARS) throw new IllegalArgumentException("query 长度不能超过 " + MAX_QUERY_CHARS);
        if (scope == null) throw new IllegalArgumentException("scope 不能为空");
    }

    /** 校验内部关联ID长度和日志安全字符。 */
    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId 不能为空");
        if (requestId.length() > 128) throw new IllegalArgumentException("requestId 不能超过128个字符");
        if (!requestId.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("requestId 包含非法字符");
        }
    }
}