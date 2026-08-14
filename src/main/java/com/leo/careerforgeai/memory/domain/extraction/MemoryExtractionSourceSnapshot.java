
package com.leo.careerforgeai.memory.domain.extraction;

import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存参与Memory提取的已完成Turn身份和内容Hash快照
 * @author: Miao Zheng
 * @date: 2026-08-14
 * @param turnId 来源Turn ID
 * @param sessionId 来源Turn所属Session
 * @param turnSequence Session内稳定顺序
 * @param sourceHash 来源正文的小写SHA-256
 **/
public record MemoryExtractionSourceSnapshot(
        UUID turnId,
        UUID sessionId,
        long turnSequence,
        String sourceHash
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public MemoryExtractionSourceSnapshot {
        Objects.requireNonNull(turnId, "turnId不能为空");
        Objects.requireNonNull(sessionId, "sessionId不能为空");

        if (turnSequence < 1) {
            throw new IllegalArgumentException("turnSequence必须从1开始");
        }
        if (sourceHash == null || !SHA256_PATTERN.matcher(sourceHash).matches()) {
            throw new IllegalArgumentException("sourceHash必须是小写SHA-256");
        }
    }

    public static MemoryExtractionSourceSnapshot from(ConversationTurn turn) {
        Objects.requireNonNull(turn, "turn不能为空");

        if (!turn.isEligibleForMemoryExtraction()) {
            throw new IllegalArgumentException("只有COMPLETED Turn可以生成提取来源快照");
        }

        return new MemoryExtractionSourceSnapshot(
                turn.turnId(),
                turn.sessionId(),
                turn.turnSequence(),
                turn.contentHash()
        );
    }
}