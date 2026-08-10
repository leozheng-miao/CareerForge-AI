package com.leo.careerforgeai.agent.application.coach;

import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存可信Career Coach回答及供API生成脱敏执行摘要的Agent Trace。
 * @author: Miao Zheng
 * @date: 2026-08-07 05:30
 **/
public record CareerCoachResult(
        CareerCoachAnswer answer,
        AgentRunTrace trace
) {

    public CareerCoachResult {
        Objects.requireNonNull(answer, "answer不能为空");
        Objects.requireNonNull(trace, "trace不能为空");

        if (trace.status() != AgentRunStatus.COMPLETED
                || trace.terminationReason() != AgentTerminationReason.FINAL_ANSWER) {
            throw new IllegalArgumentException("Career Coach成功结果必须来自正常完成的Agent Loop");
        }
    }
}