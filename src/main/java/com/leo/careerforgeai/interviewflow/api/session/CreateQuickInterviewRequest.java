package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.application.session.CreateQuickInterviewCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 接收快速面试创建幂等ID和固定方向
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record CreateQuickInterviewRequest(
        @NotNull UUID requestId,
        @NotNull
        @Pattern(regexp = "AI_AGENT_DEVELOPMENT")
        String direction
) {

    CreateQuickInterviewCommand toCommand() {
        return new CreateQuickInterviewCommand(requestId, direction);
    }
}
