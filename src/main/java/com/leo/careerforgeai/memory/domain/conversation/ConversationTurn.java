package com.leo.careerforgeai.memory.domain.conversation;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 表示会话中的用户消息、已校验助手回答或受控失败记录
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param turnId 服务端生成的消息UUID
 * @param sessionId 所属会话UUID
 * @param exchangeId 同一轮用户问题和助手回答共享的关联UUID
 * @param ownerId 消息所属用户
 * @param turnSequence 会话内严格递增的消息序号
 * @param role 消息角色
 * @param status 消息完成状态
 * @param content 正常消息正文，FAILED时为空
 * @param contentHash 正常消息正文的小写SHA-256，FAILED时为空
 * @param agentRunId 助手消息关联的Agent Run ID，用户消息时为空
 * @param failureCode 助手失败分类，COMPLETED时为空
 * @param createdAt 服务端记录时间
 **/
public record ConversationTurn(
        UUID turnId,
        UUID sessionId,
        UUID exchangeId,
        ActorId ownerId,
        long turnSequence,
        ConversationTurnRole role,
        ConversationTurnStatus status,
        String content,
        String contentHash,
        String agentRunId,
        String failureCode,
        Instant createdAt
) {

    public static final int MAX_CONTENT_LENGTH = 8_000;
    private static final int MAX_AGENT_RUN_ID_LENGTH = 128;
    private static final Pattern FAILURE_CODE_PATTERN = Pattern.compile("[A-Z0-9_]{1,64}");

    public ConversationTurn {
        Objects.requireNonNull(turnId, "turnId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(exchangeId, "exchangeId 不能为空");
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(role, "role 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");

        if (turnSequence < 1) {
            throw new IllegalArgumentException("turnSequence必须从1开始");
        }

        agentRunId = normalizeOptional(agentRunId, "agentRunId", MAX_AGENT_RUN_ID_LENGTH);
        failureCode = normalizeOptional(failureCode, "failureCode", 64);

        if (status == ConversationTurnStatus.COMPLETED) {
            content = normalizeContent(content);

            if (!calculateContentHash(content).equals(contentHash)) {
                throw new IllegalArgumentException("contentHash与消息正文不一致");
            }
            if (failureCode != null) {
                throw new IllegalArgumentException("COMPLETED消息不能包含failureCode");
            }
        } else {
            if (role != ConversationTurnRole.ASSISTANT) {
                throw new IllegalArgumentException("只有ASSISTANT消息可以记录FAILED状态");
            }
            if (content != null || contentHash != null) {
                throw new IllegalArgumentException("FAILED消息不能保存未经校验的模型内容");
            }
            if (failureCode == null || !FAILURE_CODE_PATTERN.matcher(failureCode).matches()) {
                throw new IllegalArgumentException("FAILED消息必须包含合法failureCode");
            }
        }

        if (role == ConversationTurnRole.USER && agentRunId != null) {
            throw new IllegalArgumentException("USER消息不能包含agentRunId");
        }
        if (role == ConversationTurnRole.ASSISTANT && agentRunId == null) {
            throw new IllegalArgumentException("ASSISTANT消息必须包含agentRunId");
        }
    }

    /** 创建已经校验的用户消息。 */
    public static ConversationTurn completedUser(
            UUID turnId,
            UUID sessionId,
            UUID exchangeId,
            ActorId ownerId,
            long turnSequence,
            String content,
            Instant createdAt
    ) {
        String normalizedContent = normalizeContent(content);

        return new ConversationTurn(
                turnId,
                sessionId,
                exchangeId,
                ownerId,
                turnSequence,
                ConversationTurnRole.USER,
                ConversationTurnStatus.COMPLETED,
                normalizedContent,
                calculateContentHash(normalizedContent),
                null,
                null,
                createdAt
        );
    }

    /** 创建经过Career Coach最终回答校验的助手消息。 */
    public static ConversationTurn completedAssistant(
            UUID turnId,
            UUID sessionId,
            UUID exchangeId,
            ActorId ownerId,
            long turnSequence,
            String validatedContent,
            String agentRunId,
            Instant createdAt
    ) {
        String normalizedContent = normalizeContent(validatedContent);

        return new ConversationTurn(
                turnId,
                sessionId,
                exchangeId,
                ownerId,
                turnSequence,
                ConversationTurnRole.ASSISTANT,
                ConversationTurnStatus.COMPLETED,
                normalizedContent,
                calculateContentHash(normalizedContent),
                agentRunId,
                null,
                createdAt
        );
    }

    /** 创建不包含模型原始输出的受控助手失败记录。 */
    public static ConversationTurn failedAssistant(
            UUID turnId,
            UUID sessionId,
            UUID exchangeId,
            ActorId ownerId,
            long turnSequence,
            String agentRunId,
            String failureCode,
            Instant createdAt
    ) {
        return new ConversationTurn(
                turnId,
                sessionId,
                exchangeId,
                ownerId,
                turnSequence,
                ConversationTurnRole.ASSISTANT,
                ConversationTurnStatus.FAILED,
                null,
                null,
                agentRunId,
                failureCode,
                createdAt
        );
    }

    /** 只有正常完成的消息可以作为显式Memory提取来源。 */
    public boolean isEligibleForMemoryExtraction() {
        return status == ConversationTurnStatus.COMPLETED;
    }

    /** 统一规范化会话消息，确保请求指纹与最终持久化内容使用相同口径。 */
    public static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 不能为空");
        }

        String normalized = content.strip();

        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content超过长度限制");
        }
        if (normalized.chars().anyMatch(ConversationTurn::isForbiddenControlCharacter)) {
            throw new IllegalArgumentException("content包含非法控制字符");
        }

        return normalized;
    }

    private static String normalizeOptional(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空字符串");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "超过长度限制");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + "不能包含控制字符");
        }

        return normalized;
    }

    private static boolean isForbiddenControlCharacter(int character) {
        return Character.isISOControl(character)
                && character != '\n'
                && character != '\r'
                && character != '\t';
    }

    private static String calculateContentHash(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}