package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义模拟面试按owner查询和CAS更新的持久化边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public interface MockInterviewSessionRepository {

    Optional<MockInterviewSession> findById(ActorId ownerId, UUID interviewId);

    boolean updateIfVersionMatches(
            ActorId ownerId,
            MockInterviewSession updatedSession,
            long expectedVersion
    );
}