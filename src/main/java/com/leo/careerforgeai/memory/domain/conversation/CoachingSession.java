package com.leo.careerforgeai.memory.domain.conversation;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示属于单个用户的持续求职辅导会话及其消息序号分配状态
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param sessionId 服务端生成的会话UUID
 * @param ownerId 会话所属用户
 * @param title 会话标题
 * @param status 当前会话状态
 * @param nextTurnSequence 下一条消息应使用的会话内序号
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 * @param closedAt 会话关闭时间，ACTIVE状态时为空
 **/
public record CoachingSession(
        UUID sessionId,
        ActorId ownerId,
        String title,
        CoachingSessionStatus status,
        long nextTurnSequence,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {

    public static final int MAX_TITLE_LENGTH = 120;

    public CoachingSession {
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");

        title = normalizeTitle(title);

        if (nextTurnSequence < 1) {
            throw new IllegalArgumentException("nextTurnSequence必须从1开始");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version不能小于0");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt不能早于createdAt");
        }
        if (status == CoachingSessionStatus.ACTIVE && closedAt != null) {
            throw new IllegalArgumentException("ACTIVE会话不能包含closedAt");
        }
        if (status == CoachingSessionStatus.CLOSED && closedAt == null) {
            throw new IllegalArgumentException("CLOSED会话必须包含closedAt");
        }
        if (closedAt != null && closedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("closedAt不能早于createdAt");
        }
    }

    /** 创建允许继续写入消息的新会话。 */
    public static CoachingSession create(UUID sessionId, ActorId ownerId, String title, Instant now) {
        return new CoachingSession(
                sessionId,
                ownerId,
                title,
                CoachingSessionStatus.ACTIVE,
                1,
                0,
                now,
                now,
                null
        );
    }

    /**
     * 为下一条消息占用当前序号，并把会话推进到下一个序号。
     * 调用方使用旧对象的nextTurnSequence作为新Turn序号。
     */
    public CoachingSession advanceTurnSequence(Instant now) {
        requireActive();
        requireNotBeforeUpdatedAt(now);

        return new CoachingSession(
                sessionId,
                ownerId,
                title,
                status,
                nextTurnSequence + 1,
                version + 1,
                createdAt,
                now,
                null
        );
    }

    /** 用户关闭会话，关闭后只允许读取历史。 */
    public CoachingSession close(Instant now) {
        requireActive();
        requireNotBeforeUpdatedAt(now);

        return new CoachingSession(
                sessionId,
                ownerId,
                title,
                CoachingSessionStatus.CLOSED,
                nextTurnSequence,
                version + 1,
                createdAt,
                now,
                now
        );
    }

    public boolean isActive() {
        return status == CoachingSessionStatus.ACTIVE;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("会话已经关闭");
        }
    }

    private void requireNotBeforeUpdatedAt(Instant now) {
        Objects.requireNonNull(now, "now 不能为空");

        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("操作时间不能早于会话更新时间");
        }
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 不能为空");
        }

        String normalized = title.strip();

        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title超过长度限制");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("title不能包含控制字符");
        }

        return normalized;
    }
}