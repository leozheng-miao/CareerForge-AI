package com.leo.careerforgeai.memory.application.context;

import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 保存短期完整问答、已确认长期画像和当前消息相互分离的Career Coach上下文
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param sessionId 当前会话ID
 * @param recentExchanges 经过完整轮次裁剪的短期历史
 * @param confirmedMemories 经过owner和状态过滤的长期画像事实
 * @param currentMessage 当前用户消息，不拼接历史或Memory
 * @param usage 本次动态Context实际占用和裁剪结果
 **/
public record ConversationContext(
        UUID sessionId,
        List<ConversationExchange> recentExchanges,
        List<ConfirmedMemoryFact> confirmedMemories,
        String currentMessage,
        ContextUsage usage
) {

    public ConversationContext {
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(usage, "usage 不能为空");

        recentExchanges = immutableList(
                recentExchanges,
                "recentExchanges"
        );

        confirmedMemories = immutableList(
                confirmedMemories,
                "confirmedMemories"
        );

        currentMessage = normalizeRequired(
                currentMessage,
                "currentMessage",
                8_000
        );

        int expectedMessageCount =
                recentExchanges.size() * 2 + 1 + (confirmedMemories.isEmpty() ? 0 : 1);

        if (usage.roundCount() != recentExchanges.size()) {
            throw new IllegalArgumentException(
                    "usage.roundCount与实际历史轮次不一致"
            );
        }
        if (usage.messageCount() != expectedMessageCount) {
            throw new IllegalArgumentException(
                    "usage.messageCount与实际消息数不一致"
            );
        }
        if (usage.memoryCount() != confirmedMemories.size()) {
            throw new IllegalArgumentException(
                    "usage.memoryCount与实际Memory数量不一致"
            );
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 表示同一exchangeId下完整的用户问题和已校验助手回答
     * @author: Miao Zheng
     * @date: 2026-08-12
     * @param exchangeId 一轮问答的关联ID
     * @param userTurnId 用户Turn ID
     * @param userSequence 用户消息序号
     * @param userMessage 用户消息正文
     * @param assistantTurnId 助手Turn ID
     * @param assistantSequence 助手消息序号
     * @param assistantMessage 经过最终校验的助手回答
     **/
    public record ConversationExchange(
            UUID exchangeId,
            UUID userTurnId,
            long userSequence,
            String userMessage,
            UUID assistantTurnId,
            long assistantSequence,
            String assistantMessage
    ) {

        public ConversationExchange {
            Objects.requireNonNull(exchangeId, "exchangeId 不能为空");
            Objects.requireNonNull(userTurnId, "userTurnId 不能为空");
            Objects.requireNonNull(assistantTurnId, "assistantTurnId 不能为空");

            if (userSequence < 1) {
                throw new IllegalArgumentException(
                        "userSequence必须从1开始"
                );
            }
            if (assistantSequence <= userSequence) {
                throw new IllegalArgumentException(
                        "assistantSequence必须晚于userSequence"
                );
            }

            userMessage = normalizeRequired(
                    userMessage,
                    "userMessage",
                    8_000
            );

            assistantMessage = normalizeRequired(
                    assistantMessage,
                    "assistantMessage",
                    8_000
            );
        }

        public int contentChars() {
            return userMessage.length()
                    + assistantMessage.length();
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 表示允许进入Context的最小已确认Memory投影
     * @author: Miao Zheng
     * @date: 2026-08-12
     * @param memoryId Memory ID
     * @param type Memory业务类型
     * @param normalizedKey 业务槽位或技能分组
     * @param content 用户确认的Memory正文
     **/
    public record ConfirmedMemoryFact(
            UUID memoryId,
            MemoryType type,
            MemoryNormalizedKey normalizedKey,
            String content
    ) {

        public ConfirmedMemoryFact {
            Objects.requireNonNull(memoryId, "memoryId 不能为空");
            Objects.requireNonNull(type, "type 不能为空");
            Objects.requireNonNull(
                    normalizedKey,
                    "normalizedKey 不能为空"
            );

            if (!normalizedKey.supports(type)) {
                throw new IllegalArgumentException(
                        "normalizedKey与Memory类型不匹配"
                );
            }

            content = normalizeRequired(
                    content,
                    "content",
                    2_000
            );
        }

        public int contentChars() {
            return type.name().length()
                    + normalizedKey.value().length()
                    + content.length();
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 记录动态Context预算占用和确定性裁剪结果
     * @author: Miao Zheng
     * @date: 2026-08-12
     * @param roundCount 实际历史问答轮次
     * @param messageCount 历史消息加当前消息的数量
     * @param memoryCount 实际长期Memory数量
     * @param contentChars 动态Context总字符数
     * @param estimatedTokens 估算Token数
     * @param historyTrimmed 是否裁剪过历史轮次
     * @param memoriesTrimmed 是否裁剪过长期Memory
     **/
    public record ContextUsage(
            int roundCount,
            int messageCount,
            int memoryCount,
            int contentChars,
            int estimatedTokens,
            boolean historyTrimmed,
            boolean memoriesTrimmed
    ) {

        public ContextUsage {
            if (roundCount < 0
                    || messageCount < 1
                    || memoryCount < 0
                    || contentChars < 1
                    || estimatedTokens < 1) {
                throw new IllegalArgumentException(
                        "Context使用量不能为负数或空消息"
                );
            }
        }
    }

    private static <T> List<T> immutableList(
            List<T> values,
            String fieldName
    ) {
        if (values == null) {
            throw new IllegalArgumentException(
                    fieldName + " 不能为空"
            );
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    fieldName + " 不能包含空值"
            );
        }

        return List.copyOf(values);
    }

    private static String normalizeRequired(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " 不能为空"
            );
        }

        String normalized = value.strip();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " 超过长度限制"
            );
        }
        if (normalized.chars().anyMatch(
                ConversationContext::isForbiddenControlCharacter
        )) {
            throw new IllegalArgumentException(
                    fieldName + " 包含非法控制字符"
            );
        }

        return normalized;
    }

    private static boolean isForbiddenControlCharacter(
            int character
    ) {
        return Character.isISOControl(character)
                && character != '\n'
                && character != '\r'
                && character != '\t';
    }
}