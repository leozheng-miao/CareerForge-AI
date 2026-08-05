package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.retrieval.ChunkRerankResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RerankStatus;
import com.leo.careerforgeai.knowledge.domain.retrieval.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 控制可选 Rerank，并在模型或结构化结果失败时回退到 RRF 顺序
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
@Service
@Slf4j
public class KnowledgeRerankingService {

    private final ChunkReranker chunkReranker;

    public KnowledgeRerankingService(ChunkReranker chunkReranker) {
        this.chunkReranker = chunkReranker;
    }

    /** 根据开关执行 Rerank，并始终返回一个可供后续上下文组装使用的有效顺序。 */
    public RerankedRetrievalResult rerank(String query, HybridRetrievalResult hybridResult, boolean enabled) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        if (hybridResult == null) throw new IllegalArgumentException("hybridResult 不能为空");

        List<RrfRankedChunk> rrfCandidates = hybridResult.rrfChunks();
        if (!enabled) {
            log.info("LLM Rerank已关闭，使用RRF顺序，candidates={}", rrfCandidates.size());
            return new RerankedRetrievalResult(hybridResult, rrfCandidates, RerankStatus.DISABLED, 0, null, 0, 0, 0);
        }
        if (rrfCandidates.isEmpty()) {
            log.info("LLM Rerank跳过，原因=候选为空");
            return new RerankedRetrievalResult(hybridResult, rrfCandidates, RerankStatus.SKIPPED_EMPTY, 0, null, 0, 0, 0);
        }

        long startNanos = System.nanoTime();
        try {
            ChunkRerankResult rerankResult = chunkReranker.rerank(query, rrfCandidates);
            List<RrfRankedChunk> normalized = validateAndNormalize(rrfCandidates, rerankResult.rankedChunks());
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("LLM Rerank应用成功，candidates={}, retained={}, durationMs={}", rrfCandidates.size(), normalized.size(), durationMs);
            return new RerankedRetrievalResult(
                    hybridResult,
                    normalized,
                    RerankStatus.APPLIED,
                    durationMs,
                    rerankResult.model(),
                    rerankResult.inputTokens(),
                    rerankResult.outputTokens(),
                    rerankResult.totalTokens()
            );        } catch (ChunkRerankException e) {
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("LLM Rerank失败，回退RRF，candidates={}, durationMs={}, errorType={}, error={}", rrfCandidates.size(), durationMs, e.getClass().getSimpleName(), e.getMessage());
            return new RerankedRetrievalResult(hybridResult, rrfCandidates, RerankStatus.FALLBACK, durationMs, null, 0, 0, 0);
        }
    }

    /** 再次校验端口返回结果，并只复用本次 RRF 产生的原始候选对象。 */
    private List<RrfRankedChunk> validateAndNormalize(List<RrfRankedChunk> originalCandidates, List<RrfRankedChunk> rerankedCandidates) {
        if (rerankedCandidates == null) throw new ChunkRerankException("Reranker 返回结果为空");
        if (rerankedCandidates.size() != originalCandidates.size()) throw new ChunkRerankException("Reranker 遗漏了候选 Chunk");

        Map<String, RrfRankedChunk> originalById = new LinkedHashMap<>();
        originalCandidates.forEach(candidate -> originalById.put(candidate.chunk().chunkId(), candidate));

        Set<String> seenIds = new HashSet<>();
        List<RrfRankedChunk> normalized = new ArrayList<>(rerankedCandidates.size());
        for (RrfRankedChunk candidate : rerankedCandidates) {
            if (candidate == null) throw new ChunkRerankException("Reranker 返回结果包含 null");
            String chunkId = candidate.chunk().chunkId();
            if (!seenIds.add(chunkId)) throw new ChunkRerankException("Reranker 返回了重复 Chunk ID=" + chunkId);

            RrfRankedChunk original = originalById.get(chunkId);
            if (original == null) throw new ChunkRerankException("Reranker 返回了未知 Chunk ID=" + chunkId);
            normalized.add(original);
        }

        if (seenIds.size() != originalById.size()) throw new ChunkRerankException("Reranker 输出与 RRF 候选集合不一致");
        return List.copyOf(normalized);
    }
}