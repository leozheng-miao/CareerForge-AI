package com.leo.careerforgeai.agent.application.port.run;

import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitDecision;
import com.leo.careerforgeai.shared.actor.ActorId;

/**
 * @program: CareerForge-AI
 * @description: 定义新Coaching Run的owner维度原子限流边界
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
public interface CoachingRunRateLimiter {

    CoachingRunRateLimitDecision acquire(ActorId ownerId);
}