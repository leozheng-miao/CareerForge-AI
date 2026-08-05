package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import com.leo.careerforgeai.model.application.EmbeddingGateway;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingPurpose;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 接收原始问题，分别执行 BM25 与 Query Embedding + kNN，返回两路可对照结果
 * @author: Miao Zheng
 * @date: 2026-08-04 15:30
 **/
@Service
@Slf4j
public class KnowledgeRetrievalService {

    private static final int MAX_TOP_K = 100;
    private static final int MAX_NUM_CANDIDATES = 10_000;

    private final Bm25Retriever bm25Retriever;
    private final VectorRetriever vectorRetriever;
    private final EmbeddingGateway embeddingGateway;
    private final RrfFusion rrfFusion;

    public KnowledgeRetrievalService(Bm25Retriever bm25Retriever, VectorRetriever vectorRetriever, EmbeddingGateway embeddingGateway, RrfFusion rrfFusion) {
        this.bm25Retriever = bm25Retriever;
        this.vectorRetriever = vectorRetriever;
        this.embeddingGateway = embeddingGateway;
        this.rrfFusion = rrfFusion;
    }

    /** 召回两路候选并通过 Java RRF 生成最终混合排名。 */
    public HybridRetrievalResult retrieveHybrid(String query, RetrievalScope scope, int candidateTopK, int numCandidates, int finalTopK) {
        validateFinalTopK(finalTopK);

        RetrievalComparisonResult comparisonResult = retrieveBoth(query, scope, candidateTopK, numCandidates);
        long startNanos = System.nanoTime();
        List<RrfRankedChunk> rrfChunks = rrfFusion.fuse(comparisonResult.bm25Result(), comparisonResult.vectorResult(), finalTopK);
        long fusionDurationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        HybridRetrievalResult result = new HybridRetrievalResult(comparisonResult, rrfChunks, fusionDurationMs);
        log.info("RRF混合检索完成，candidateTopK={}, finalTopK={}, bm25Hits={}, vectorHits={}, fusedHits={}, fusionDurationMs={}", candidateTopK, finalTopK, comparisonResult.bm25Result().chunks().size(), comparisonResult.vectorResult().chunks().size(), rrfChunks.size(), fusionDurationMs);
        return result;
    }

    /** 对同一个原始问题分别执行 BM25 和 Query Embedding + kNN 检索。 */
    public RetrievalComparisonResult retrieveBoth(String query, RetrievalScope scope, int topK, int numCandidates) {
        validateInput(query, scope, topK, numCandidates);

        RetrievalResult bm25Result = bm25Retriever.retrieve(query, scope, topK);
        EmbeddingResult queryEmbedding = embeddingGateway.embed(new EmbeddingRequest(EmbeddingPurpose.QUERY, List.of(query)));
        if (queryEmbedding.vectors().size() != 1) throw new IllegalStateException("Query Embedding 必须返回一个向量");

        RetrievalResult vectorResult = vectorRetriever.retrieve(queryEmbedding.vectors().getFirst(), scope, topK, numCandidates);
        RetrievalComparisonResult result = new RetrievalComparisonResult(bm25Result, vectorResult, queryEmbedding.model(), queryEmbedding.dimensions(), queryEmbedding.durationMs());

        log.info("双路检索完成，topK={}, numCandidates={}, bm25Hits={}, vectorHits={}, bm25DurationMs={}, queryEmbeddingDurationMs={}, vectorSearchDurationMs={}", topK, numCandidates, bm25Result.chunks().size(), vectorResult.chunks().size(), bm25Result.durationMs(), queryEmbedding.durationMs(), vectorResult.durationMs());
        return result;
    }

    private void validateInput(String query, RetrievalScope scope, int topK, int numCandidates) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        if (scope == null) throw new IllegalArgumentException("scope 不能为空");
        if (topK <= 0 || topK > MAX_TOP_K) throw new IllegalArgumentException("topK 必须在 1 到 " + MAX_TOP_K + " 之间");
        if (numCandidates < topK || numCandidates > MAX_NUM_CANDIDATES) throw new IllegalArgumentException("numCandidates 必须大于等于 topK 且不超过 " + MAX_NUM_CANDIDATES);
    }

    /** 在调用外部检索服务前校验最终融合返回数量。 */
    private void validateFinalTopK(int finalTopK) {
        if (finalTopK <= 0 || finalTopK > MAX_TOP_K) throw new IllegalArgumentException("finalTopK 必须在 1 到 " + MAX_TOP_K + " 之间");
    }
}