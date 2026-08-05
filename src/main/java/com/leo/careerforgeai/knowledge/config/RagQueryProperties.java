package com.leo.careerforgeai.knowledge.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @program: CareerForge-AI
 * @description: 配置完整 RAG 查询链路的候选数量、Rerank 开关和上下文预算
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
@Getter
@Validated
@ConfigurationProperties(prefix = "careerforge.knowledge.query", ignoreUnknownFields = false)
public final class RagQueryProperties {

    private static final int MAX_TOP_K = 100;
    private static final int MAX_NUM_CANDIDATES = 10_000;
    private static final int MAX_CONTENT_CHARS = 100_000;

    private final int candidateTopK;
    private final int numCandidates;
    private final int finalTopK;
    private final boolean rerankEnabled;
    private final int maxContentChars;

    public RagQueryProperties(int candidateTopK, int numCandidates, int finalTopK, boolean rerankEnabled, int maxContentChars) {
        if (candidateTopK <= 0 || candidateTopK > MAX_TOP_K) throw new IllegalArgumentException("candidateTopK 必须在 1 到 " + MAX_TOP_K + " 之间");
        if (numCandidates < candidateTopK || numCandidates > MAX_NUM_CANDIDATES) throw new IllegalArgumentException("numCandidates 必须大于等于 candidateTopK 且不超过 " + MAX_NUM_CANDIDATES);
        if (finalTopK <= 0 || finalTopK > MAX_TOP_K) throw new IllegalArgumentException("finalTopK 必须在 1 到 " + MAX_TOP_K + " 之间");
        if (maxContentChars <= 0 || maxContentChars > MAX_CONTENT_CHARS) throw new IllegalArgumentException("maxContentChars 必须在 1 到 " + MAX_CONTENT_CHARS + " 之间");
        this.candidateTopK = candidateTopK;
        this.numCandidates = numCandidates;
        this.finalTopK = finalTopK;
        this.rerankEnabled = rerankEnabled;
        this.maxContentChars = maxContentChars;
    }
}