package com.leo.careerforgeai.knowledge.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @program: CareerForge-AI
 * @description: 绑定并校验 Markdown Chunk 最大字符数、重叠字符数和有效策略版本
 * @author: Miao Zheng
 * @date: 2026-07-31
 **/
@Getter
@Validated
@ConfigurationProperties(prefix = "careerforge.knowledge.chunking", ignoreUnknownFields = false)
public final class ChunkingProperties {

    public static final String ALGORITHM_VERSION = "markdown-structure-v2";

    private final int maxChunkChars;
    private final int overlapChars;

    public ChunkingProperties(int maxChunkChars, int overlapChars) {
        if (maxChunkChars <= 0) throw new IllegalArgumentException("maxChunkChars 必须大于 0");
        if (overlapChars < 0) throw new IllegalArgumentException("overlapChars 不能小于 0");
        if (overlapChars >= maxChunkChars) throw new IllegalArgumentException("overlapChars 必须小于 maxChunkChars");
        this.maxChunkChars = maxChunkChars;
        this.overlapChars = overlapChars;
    }

    public String chunkerVersion() {
        return ALGORITHM_VERSION + "|max=" + maxChunkChars + "|overlap=" + overlapChars;
    }
}