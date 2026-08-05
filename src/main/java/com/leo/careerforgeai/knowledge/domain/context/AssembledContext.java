package com.leo.careerforgeai.knowledge.domain.context;

import com.leo.careerforgeai.knowledge.domain.DocumentChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 保存最终进入回答 Prompt 的 Chunk、字符预算和去重统计
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public record AssembledContext(
        List<DocumentChunk> chunks,
        int usedContentChars,
        int maxContentChars,
        int duplicateSkippedCount,
        int budgetSkippedCount,
        String assemblerVersion
) {

    public AssembledContext {
        if (chunks == null) throw new IllegalArgumentException("chunks 不能为空");
        if (chunks.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("chunks 不能包含 null");
        if (maxContentChars <= 0) throw new IllegalArgumentException("maxContentChars 必须大于 0");
        if (usedContentChars < 0 || usedContentChars > maxContentChars) throw new IllegalArgumentException("usedContentChars 超出预算");
        if (duplicateSkippedCount < 0) throw new IllegalArgumentException("duplicateSkippedCount 不能小于 0");
        if (budgetSkippedCount < 0) throw new IllegalArgumentException("budgetSkippedCount 不能小于 0");
        if (assemblerVersion == null || assemblerVersion.isBlank()) throw new IllegalArgumentException("assemblerVersion 不能为空");
        chunks = List.copyOf(chunks);

        Set<String> chunkIds = new HashSet<>();
        chunks.forEach(chunk -> {
            if (!chunkIds.add(chunk.chunkId())) throw new IllegalArgumentException("chunks 包含重复 Chunk ID");
        });
    }
}