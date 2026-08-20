package com.leo.careerforgeai.agent.application.port.run;

import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义Coaching Run插入、owner隔离查询和CAS状态更新持久化边界
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public interface CoachingRunRepository {

    CoachingRun claim(CoachingRun candidate);

    Optional<CoachingRun> findByRunId(
            ActorId ownerId,
            UUID runId
    );

    Optional<CoachingRun> findByRequestId(
            ActorId ownerId,
            UUID requestId
    );

    boolean updateIfVersionMatches(
            ActorId ownerId,
            CoachingRun updatedRun,
            long expectedVersion
    );
}