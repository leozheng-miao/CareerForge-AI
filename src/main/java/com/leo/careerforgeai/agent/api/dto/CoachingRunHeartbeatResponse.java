
package com.leo.careerforgeai.agent.api.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示不推进Last-Event-ID的Run SSE心跳
 * @author: Miao Zheng
 * @date: 2026-08-21
 * @param runId Run唯一标识
 * @param sentAt 心跳发送时间
 */
public record CoachingRunHeartbeatResponse(UUID runId, Instant sentAt) {

    public CoachingRunHeartbeatResponse {
        Objects.requireNonNull(runId, "runId不能为空");
        Objects.requireNonNull(sentAt, "sentAt不能为空");
    }
}