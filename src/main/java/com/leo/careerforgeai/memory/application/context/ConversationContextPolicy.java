package com.leo.careerforgeai.memory.application.context;

/**
 * @program: CareerForge-AI
 * @description: 定义会话Context的轮次、消息、Memory、字符和估算Token预算
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param maxRounds 最多保留的完整历史问答轮次
 * @param maxMessages 最多保留的动态消息数，包含当前用户消息
 * @param maxMemories 最多注入的已确认长期Memory数量
 * @param maxContentChars 历史、Memory和当前消息的总字符预算
 * @param maxEstimatedTokens 动态Context的估算Token预算
 * @param charsPerEstimatedToken 估算一个Token对应的字符数
 **/
public record ConversationContextPolicy(
        int maxRounds,
        int maxMessages,
        int maxMemories,
        int maxContentChars,
        int maxEstimatedTokens,
        int charsPerEstimatedToken
) {

    public ConversationContextPolicy {
        if (maxRounds < 0 || maxRounds > 50) {
            throw new IllegalArgumentException("maxRounds必须在0到50之间");
        }
        if (maxMessages < 1 || maxMessages > 101) {
            throw new IllegalArgumentException("maxMessages必须在1到101之间");
        }
        if (maxMemories < 0 || maxMemories > 100) {
            throw new IllegalArgumentException("maxMemories必须在0到100之间");
        }
        if (maxContentChars < 1 || maxContentChars > 100_000) {
            throw new IllegalArgumentException("maxContentChars超出允许范围");
        }
        if (maxEstimatedTokens < 1 || maxEstimatedTokens > 50_000) {
            throw new IllegalArgumentException("maxEstimatedTokens超出允许范围");
        }
        if (charsPerEstimatedToken < 1 || charsPerEstimatedToken > 8) {
            throw new IllegalArgumentException("charsPerEstimatedToken必须在1到8之间");
        }
    }

    /** 创建检查点3使用的首版受控预算。 */
    public static ConversationContextPolicy defaults() {
        return new ConversationContextPolicy(
                6,
                14,
                20,
                12_000,
                6_000,
                2
        );
    }

    /** 使用确定性字符规则估算Token，只用于预算保护，不表示供应商精确计费。 */
    public int estimateTokens(int contentChars) {
        if (contentChars < 0) {
            throw new IllegalArgumentException("contentChars不能小于0");
        }

        return contentChars == 0
                ? 0
                : (contentChars + charsPerEstimatedToken - 1)
                / charsPerEstimatedToken;
    }
}