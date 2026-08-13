package com.leo.careerforgeai.agent.application.coach;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回会话式Career Coach结果和完成本轮后的Session版本
 * @author: Miao Zheng
 * @date: 2026-08-13
 * @param sessionId 当前会话ID
 * @param sessionVersion 成功保存助手Turn后的会话版本
 * @param coachResult 经过最终校验的Career Coach结果
 **/
public record ConversationalCareerCoachResult(
        UUID sessionId,
        long sessionVersion,
        CareerCoachResult coachResult
) {

    public ConversationalCareerCoachResult {
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        Objects.requireNonNull(coachResult, "coachResult不能为空");
        if (sessionVersion < 0) {
            throw new IllegalArgumentException("sessionVersion不能小于0");
        }
    }
}