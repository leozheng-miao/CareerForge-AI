package com.leo.careerforgeai.memory.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存Memory候选的来源类型、来源标识和来源内容哈希
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public record MemorySource(
        MemorySourceType sourceType,
        String sourceId,
        String sourceHash
) {

    private static final int MAX_SOURCE_ID_LENGTH = 128;
    private static final Pattern SHA256_PATTERN =
            Pattern.compile("[0-9a-f]{64}");

    public MemorySource {
        Objects.requireNonNull(sourceType, "sourceType 不能为空");

        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId 不能为空");
        }

        sourceId = sourceId.strip();

        if (sourceId.length() > MAX_SOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("sourceId 超过长度限制");
        }
        if (sourceId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("sourceId 不能包含控制字符");
        }
        if (sourceHash == null
                || !SHA256_PATTERN.matcher(sourceHash).matches()) {
            throw new IllegalArgumentException(
                    "sourceHash 必须是小写SHA-256"
            );
        }
    }
}