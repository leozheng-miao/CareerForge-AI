package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.execution.InterviewNodeExecution;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义Graph节点执行权认领、逻辑身份查询和CAS更新边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public interface InterviewNodeExecutionRepository {

    InterviewNodeExecution claim(InterviewNodeExecution candidate);

    Optional<InterviewNodeExecution> findById(
            ActorId ownerId,
            UUID interviewId,
            UUID executionId
    );

    Optional<InterviewNodeExecution> findByIdentity(
            ActorId ownerId,
            UUID interviewId,
            int roundNo,
            String nodeName,
            String inputHash
    );

    boolean updateIfVersionMatches(
            ActorId ownerId,
            InterviewNodeExecution updatedExecution,
            long expectedVersion
    );

    int sumModelCallCount(ActorId ownerId, UUID interviewId);

    long sumTotalTokens(ActorId ownerId, UUID interviewId);
}