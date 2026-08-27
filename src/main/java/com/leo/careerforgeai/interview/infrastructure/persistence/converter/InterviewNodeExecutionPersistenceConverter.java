package com.leo.careerforgeai.interview.infrastructure.persistence.converter;

import com.leo.careerforgeai.interview.domain.InterviewNodeExecution;
import com.leo.careerforgeai.interview.domain.InterviewNodeExecutionStatus;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewNodeExecutionEntity;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 转换Graph节点执行领域对象和数据库Entity
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public class InterviewNodeExecutionPersistenceConverter {

    public InterviewNodeExecutionEntity toEntity(InterviewNodeExecution execution) {
        Objects.requireNonNull(execution, "execution不能为空");
        ModelUsage usage = execution.modelUsage();

        InterviewNodeExecutionEntity entity = new InterviewNodeExecutionEntity();
        entity.setExecutionId(execution.executionId().toString());
        entity.setInterviewId(execution.interviewId().toString());
        entity.setOwnerId(execution.ownerId().value());
        entity.setRoundNo(execution.roundNo());
        entity.setNodeName(execution.nodeName());
        entity.setInputHash(execution.inputHash());
        entity.setExecutionStatus(execution.status().name());
        entity.setOutputReferenceId(execution.outputReferenceId());
        entity.setModelRequestId(execution.modelRequestId());
        entity.setAttemptCount(execution.attemptCount());
        entity.setModelCallCount(execution.modelCallCount());
        entity.setInputTokens(usage.inputTokens());
        entity.setOutputTokens(usage.outputTokens());
        entity.setTotalTokens(usage.totalTokens());
        entity.setModelDurationMs(execution.modelDurationMs());
        entity.setFailureCode(execution.failureCode());
        entity.setVersion(execution.version());
        entity.setStartedAt(execution.startedAt());
        entity.setFinishedAt(execution.finishedAt());
        entity.setCreatedAt(execution.createdAt());
        entity.setUpdatedAt(execution.updatedAt());
        return entity;
    }

    public InterviewNodeExecution toDomain(InterviewNodeExecutionEntity entity) {
        return new InterviewNodeExecution(
                UUID.fromString(entity.getExecutionId()),
                UUID.fromString(entity.getInterviewId()),
                new ActorId(entity.getOwnerId()),
                requireInteger(entity.getRoundNo(), "roundNo"),
                entity.getNodeName(),
                entity.getInputHash(),
                InterviewNodeExecutionStatus.valueOf(entity.getExecutionStatus()),
                entity.getOutputReferenceId(),
                entity.getModelRequestId(),
                requireInteger(entity.getAttemptCount(), "attemptCount"),
                requireInteger(entity.getModelCallCount(), "modelCallCount"),
                new ModelUsage(
                        requireLong(entity.getInputTokens(), "inputTokens"),
                        requireLong(entity.getOutputTokens(), "outputTokens"),
                        requireLong(entity.getTotalTokens(), "totalTokens")
                ),
                requireLong(entity.getModelDurationMs(), "modelDurationMs"),
                entity.getFailureCode(),
                requireLong(entity.getVersion(), "version"),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
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