package com.leo.careerforgeai.interview.api.dto.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回模拟面试SSE连接心跳且不携带业务敏感内容
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param interviewId 模拟面试UUID
 * @param sentAt 心跳发送时间
 **/
public record InterviewHeartbeatResponse(
        UUID interviewId,
        Instant sentAt
) {

    public InterviewHeartbeatResponse {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(sentAt, "sentAt不能为空");
    }
}