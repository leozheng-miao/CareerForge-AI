package com.leo.careerforgeai.interview.infrastructure.persistence.converter;

import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.MockInterviewSessionEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在模拟面试领域对象和MyBatis-Plus Entity之间执行受控转换
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public final class MockInterviewSessionPersistenceConverter {

    public MockInterviewSessionEntity toEntity(MockInterviewSession session) {
        Objects.requireNonNull(session, "session不能为空");
        InterviewBudgetPolicy budget = session.budgetPolicy();

        MockInterviewSessionEntity entity = new MockInterviewSessionEntity();
        entity.setInterviewId(session.interviewId().toString());
        entity.setOwnerId(session.ownerId().value());
        entity.setRequestId(session.requestId().toString());
        entity.setRequestFingerprint(session.requestFingerprint());
        entity.setInputSnapshotId(session.inputSnapshotId().toString());
        entity.setInputSnapshotHash(session.inputSnapshotHash());
        entity.setInterviewMode(session.mode().name());
        entity.setInterviewStatus(session.status().name());
        entity.setMaxQuestions(budget.maxQuestions());
        entity.setMaxFollowUps(budget.maxFollowUps());
        entity.setMaxModelCalls(budget.maxModelCalls());
        entity.setMaxTotalTokens(budget.maxTotalTokens());
        entity.setFailureCode(session.failureCode() == null ? null : session.failureCode().name());
        entity.setVersion(session.version());
        entity.setCreatedAt(session.createdAt());
        entity.setUpdatedAt(session.updatedAt());
        entity.setFinishedAt(session.finishedAt());
        return entity;
    }

    public MockInterviewSession toDomain(MockInterviewSessionEntity entity) {
        Objects.requireNonNull(entity, "entity不能为空");

        InterviewBudgetPolicy budget = new InterviewBudgetPolicy(
                requireInteger(entity.getMaxQuestions(), "maxQuestions"),
                requireInteger(entity.getMaxFollowUps(), "maxFollowUps"),
                requireInteger(entity.getMaxModelCalls(), "maxModelCalls"),
                requireLong(entity.getMaxTotalTokens(), "maxTotalTokens")
        );

        return new MockInterviewSession(
                UUID.fromString(entity.getInterviewId()),
                new ActorId(entity.getOwnerId()),
                UUID.fromString(entity.getRequestId()),
                entity.getRequestFingerprint(),
                InterviewMode.valueOf(entity.getInterviewMode()),
                UUID.fromString(entity.getInputSnapshotId()),
                entity.getInputSnapshotHash(),
                InterviewStatus.valueOf(entity.getInterviewStatus()),
                budget,
                entity.getFailureCode() == null ? null : InterviewFailureCode.valueOf(entity.getFailureCode()),
                requireLong(entity.getVersion(), "version"),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getFinishedAt()
        );
    }

    private static int requireInteger(Integer value, String fieldName) {
        if (value == null) throw new IllegalStateException("数据库" + fieldName + "不能为空");
        return value;
    }

    private static long requireLong(Long value, String fieldName) {
        if (value == null) throw new IllegalStateException("数据库" + fieldName + "不能为空");
        return value;
    }
}