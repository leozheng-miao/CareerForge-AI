package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Duration;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义可丢失的Redis页面活动租约边界
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public interface InterviewActivityLease {

    void refresh(
            ActorId ownerId,
            UUID interviewId,
            Duration leaseDuration
    );

    boolean isActive(ActorId ownerId, UUID interviewId);

    void revoke(ActorId ownerId, UUID interviewId);
}
