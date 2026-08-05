package com.leo.careerforgeai.knowledge.application.retrieval;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: CareerForge-AI
 * @description: 按 Chunk ID 合并 BM25 与向量排名并计算稳定的 RRF 排序
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
@Component
public class RrfFusion {

    /**
     * RANK_CONSTANT -
     * 数值较小：更强调前几名之间的差距。
     * 数值较大：减弱 Rank 1 与 Rank 5 的差距，更奖励两路共同出现。
     */
    private static final int RANK_CONSTANT = 60;
    private static final int MAX_TOP_K = 100;
    private static final Comparator<Candidate> RANKING = Comparator.comparingDouble(Candidate::rrfScore).reversed().thenComparing(candidate -> candidate.chunk.chunkId());

    /** 将 BM25 与向量结果按 Chunk ID 进行 RRF 融合并返回最终 Top K。 */
    public List<RrfRankedChunk> fuse(RetrievalResult bm25Result, RetrievalResult vectorResult, int topK) {
        validateInput(bm25Result, vectorResult, topK);

        Map<String, Candidate> candidates = new HashMap<>();
        mergeRoute(candidates, bm25Result, true);
        mergeRoute(candidates, vectorResult, false);

        List<Candidate> rankedCandidates = new ArrayList<>(candidates.values());
        rankedCandidates.sort(RANKING);

        int resultSize = Math.min(topK, rankedCandidates.size());
        List<RrfRankedChunk> result = new ArrayList<>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            Candidate candidate = rankedCandidates.get(index);
            result.add(new RrfRankedChunk(candidate.chunk, candidate.bm25Rank, candidate.vectorRank, candidate.rrfScore(), index + 1));
        }
        return List.copyOf(result);
    }

    /** 将一条召回路线写入候选集合，并拒绝同一路线中的重复 Chunk。 */
    private void mergeRoute(Map<String, Candidate> candidates, RetrievalResult retrievalResult, boolean bm25Route) {
        for (RetrievedChunk retrievedChunk : retrievalResult.chunks()) {
            DocumentChunk chunk = retrievedChunk.chunk();
            Candidate candidate = candidates.computeIfAbsent(chunk.chunkId(), ignored -> new Candidate(chunk));
            if (!candidate.chunk.equals(chunk)) throw new IllegalArgumentException("相同 chunkId 对应的 Chunk 数据不一致，chunkId=" + chunk.chunkId());

            if (bm25Route) {
                if (candidate.bm25Rank != null) throw new IllegalArgumentException("BM25 结果包含重复 chunkId=" + chunk.chunkId());
                candidate.bm25Rank = retrievedChunk.rank();
            } else {
                if (candidate.vectorRank != null) throw new IllegalArgumentException("Vector 结果包含重复 chunkId=" + chunk.chunkId());
                candidate.vectorRank = retrievedChunk.rank();
            }
        }
    }

    /** 校验 RRF 输入结果和最终返回数量。 */
    private void validateInput(RetrievalResult bm25Result, RetrievalResult vectorResult, int topK) {
        if (bm25Result == null) throw new IllegalArgumentException("bm25Result 不能为空");
        if (vectorResult == null) throw new IllegalArgumentException("vectorResult 不能为空");
        if (topK <= 0 || topK > MAX_TOP_K) throw new IllegalArgumentException("topK 必须在 1 到 " + MAX_TOP_K + " 之间");
    }

    private static final class Candidate {

        private final DocumentChunk chunk;
        private Integer bm25Rank;
        private Integer vectorRank;

        private Candidate(DocumentChunk chunk) {
            this.chunk = chunk;
        }

        /** 根据候选在两条召回路线中的排名计算 RRF Score。 */
        /**
         * RRF Score = 1 / (rankConstant + bm25Rank)
         *           + 1 / (rankConstant + vectorRank)
         * @return
         */
        private double rrfScore() {
            double score = 0;
            if (bm25Rank != null) score += 1D / (RANK_CONSTANT + bm25Rank);
            if (vectorRank != null) score += 1D / (RANK_CONSTANT + vectorRank);
            return score;
        }
    }
}