package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 观察Agent Loop白名单工具的开始和完成，不接收参数、结果或内部异常
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
public interface AgentLoopObserver {

    default void toolStarted(String toolName, Instant occurredAt) {
    }

    default void toolCompleted(
            String toolName,
            ToolExecutionStatus status,
            Instant occurredAt
    ) {
    }

    static AgentLoopObserver noOp() {
        return new AgentLoopObserver() {
        };
    }
}