package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.answer.RagAnswer;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 编排混合检索、可选重排、上下文组装和带引用回答的完整 RAG 查询用例
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
@Service
@Slf4j
public class RagQueryService {

    private static final int MAX_QUERY_CHARS = 2_000;

    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeRerankingService rerankingService;
    private final ContextAssembler contextAssembler;
    private final RagAnswerService answerService;
    private final RagQueryProperties properties;

    public RagQueryService(
            KnowledgeRetrievalService retrievalService,
            KnowledgeRerankingService rerankingService,
            ContextAssembler contextAssembler,
            RagAnswerService answerService,
            RagQueryProperties properties
    ) {
        this.retrievalService = retrievalService;
        this.rerankingService = rerankingService;
        this.contextAssembler = contextAssembler;
        this.answerService = answerService;
        this.properties = properties;
    }

    /** 执行一次完整 RAG 查询，并保留后续接口和观测需要的各阶段结果。 */
    public RagQueryResult query(String query, RetrievalScope scope) {
        validateInput(query, scope);

        String requestId = UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        log.info("RAG查询开始，requestId={}, knowledgeBaseId={}", requestId, scope.knowledgeBaseId());

        try {
            HybridRetrievalResult retrievalResult = retrievalService.retrieveHybrid(
                    query,
                    scope,
                    properties.getCandidateTopK(),
                    properties.getNumCandidates(),
                    properties.getFinalTopK()
            );
            RerankedRetrievalResult rerankedResult = rerankingService.rerank(query, retrievalResult, properties.isRerankEnabled());
            AssembledContext context = contextAssembler.assemble(rerankedResult.rankedChunks(), properties.getMaxContentChars());
            RagAnswer answer = answerService.answer(query, context);
            long totalDurationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            RagQueryResult result = new RagQueryResult(requestId, retrievalResult, rerankedResult, context, answer, totalDurationMs);
            log.info("RAG查询完成，requestId={}, knowledgeBaseId={}, answerStatus={}, rrfCandidates={}, rerankStatus={}, contextChunks={}, citations={}, totalDurationMs={}", requestId, scope.knowledgeBaseId(), answer.status(), retrievalResult.rrfChunks().size(), rerankedResult.status(), context.chunks().size(), answer.citations().size(), totalDurationMs);
            return result;
        } catch (RuntimeException e) {
            long totalDurationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.error("RAG查询失败，requestId={}, knowledgeBaseId={}, errorType={}, error={}, totalDurationMs={}", requestId, scope.knowledgeBaseId(), e.getClass().getSimpleName(), e.getMessage(), totalDurationMs);
            throw e;
        }
    }

    private void validateInput(String query, RetrievalScope scope) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        if (query.length() > MAX_QUERY_CHARS) throw new IllegalArgumentException("query 长度不能超过 " + MAX_QUERY_CHARS);
        if (scope == null) throw new IllegalArgumentException("scope 不能为空");
    }
}