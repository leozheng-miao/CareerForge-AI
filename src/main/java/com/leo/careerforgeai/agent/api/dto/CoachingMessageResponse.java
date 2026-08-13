package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.agent.application.coach.ConversationalCareerCoachResult;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回会话式Career Coach回答和下一次写入应使用的Session版本
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
public record CoachingMessageResponse(
        UUID sessionId,
        long sessionVersion,
        CareerCoachResponse coach
) {

    public CoachingMessageResponse {
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        Objects.requireNonNull(coach, "coach不能为空");
        if (sessionVersion < 0) {
            throw new IllegalArgumentException("sessionVersion不能小于0");
        }
    }

    public static CoachingMessageResponse from(
            ConversationalCareerCoachResult result
    ) {
        return new CoachingMessageResponse(
                result.sessionId(),
                result.sessionVersion(),
                CareerCoachResponse.from(result.coachResult())
        );
    }
}