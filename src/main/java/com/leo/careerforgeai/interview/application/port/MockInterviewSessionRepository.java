package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义模拟面试幂等认领、owner查询和CAS更新的持久化边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public interface MockInterviewSessionRepository {

    MockInterviewSession claim(MockInterviewSession candidate);

    Optional<MockInterviewSession> findById(ActorId ownerId, UUID interviewId);

    Optional<MockInterviewSession> findByRequestId(ActorId ownerId, UUID requestId);

    boolean updateIfVersionMatches(ActorId ownerId, MockInterviewSession updatedSession, long expectedVersion);

}