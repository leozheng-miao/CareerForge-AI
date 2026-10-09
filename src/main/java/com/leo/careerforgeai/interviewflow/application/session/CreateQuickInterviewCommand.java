package com.leo.careerforgeai.interviewflow.application.session;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 承载快速面试幂等创建请求和固定岗位方向
 * @author: Miao Zheng
 * @date: 2026-10-06
 * @param requestId 客户端生成的创建幂等ID
 * @param direction 首版固定的AI Agent开发方向
 */
public record CreateQuickInterviewCommand(
        UUID requestId,
        String direction
) {

    public static final String AI_AGENT_DEVELOPMENT =
            "AI_AGENT_DEVELOPMENT";

    public CreateQuickInterviewCommand {
        Objects.requireNonNull(requestId, "requestId不能为null");
        if (!AI_AGENT_DEVELOPMENT.equals(direction)) {
            throw new IllegalArgumentException(
                    "快速面试岗位方向必须为AI_AGENT_DEVELOPMENT"
            );
        }
    }
}
