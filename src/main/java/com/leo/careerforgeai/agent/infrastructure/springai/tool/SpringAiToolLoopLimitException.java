package com.leo.careerforgeai.agent.infrastructure.springai.tool;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 在Spring AI发起下一次模型调用前以稳定语义终止越界循环。
 * @author: Miao Zheng
 * @date: 2026-08-10 05:00
 **/
public final class SpringAiToolLoopLimitException extends RuntimeException {

    private final SpringAiToolLoopLimitType limitType;

    public SpringAiToolLoopLimitException(SpringAiToolLoopLimitType limitType) {
        super(safeMessage(limitType));
        this.limitType = Objects.requireNonNull(limitType, "limitType不能为空");
    }

    public SpringAiToolLoopLimitType getLimitType() {
        return limitType;
    }

    private static String safeMessage(SpringAiToolLoopLimitType limitType) {
        Objects.requireNonNull(limitType, "limitType不能为空");
        return switch (limitType) {
            case MAX_MODEL_ITERATIONS -> "Spring AI模型迭代次数达到上限";
            case DEADLINE_EXCEEDED -> "Spring AI Agent Deadline已到期";
            case MAX_TOTAL_TOOL_CALLS -> "Spring AI工具调用总次数达到上限";
            case MAX_CALLS_PER_TOOL -> "Spring AI单工具调用次数达到上限";
            case REPEATED_TOOL_CALL -> "Spring AI检测到重复工具调用";
        };
    }
}