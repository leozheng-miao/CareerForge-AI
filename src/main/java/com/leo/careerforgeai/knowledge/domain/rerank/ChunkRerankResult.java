package com.leo.careerforgeai.knowledge.domain.rerank;

import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;

import java.util.List;
import java.util.Objects;

/*/
保存 Reranker 返回顺序及本次模型调用的模型名和 Token
 */
public record ChunkRerankResult(
        List<RrfRankedChunk> rankedChunks,
        String model,
        long inputTokens,
        long outputTokens,
        long totalTokens
) {

    public ChunkRerankResult {
        if (rankedChunks == null) throw new IllegalArgumentException("rankedChunks 不能为空");
        if (rankedChunks.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("rankedChunks 不能包含 null");
        rankedChunks = List.copyOf(rankedChunks);
        if (!rankedChunks.isEmpty() && (model == null || model.isBlank())) throw new IllegalArgumentException("非空 Rerank 结果必须包含 model");
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) throw new IllegalArgumentException("Token 数量不能小于 0");
        if (totalTokens != inputTokens + outputTokens) throw new IllegalArgumentException("totalTokens 必须等于 inputTokens + outputTokens");
        if (rankedChunks.isEmpty() && (model != null || totalTokens != 0)) throw new IllegalArgumentException("空候选不能包含模型调用信息");
    }

    /** 表示空候选时没有执行模型调用。 */
    public static ChunkRerankResult notCalled() {
        return new ChunkRerankResult(List.of(), null, 0, 0, 0);
    }
}