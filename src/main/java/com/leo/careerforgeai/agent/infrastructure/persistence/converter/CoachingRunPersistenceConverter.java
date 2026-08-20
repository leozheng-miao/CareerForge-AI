package com.leo.careerforgeai.agent.infrastructure.persistence.converter;

import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.infrastructure.persistence.entity.CoachingRunEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在Coaching Run领域对象和MyBatis-Plus Entity之间执行受控转换
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Component
public final class CoachingRunPersistenceConverter {

    public CoachingRunEntity toEntity(CoachingRun run) {
        Objects.requireNonNull(run, "run不能为空");

        CoachingRunEntity entity = new CoachingRunEntity();
        entity.setRunId(run.runId().toString());
        entity.setOwnerId(run.ownerId().value());
        entity.setSessionId(run.sessionId().toString());
        entity.setRequestId(run.requestId().toString());
        entity.setRequestFingerprint(run.requestFingerprint());
        entity.setExpectedSessionVersion(
                run.expectedSessionVersion()
        );
        entity.setRunStatus(run.status().name());
        entity.setUserTurnId(toNullableString(run.userTurnId()));
        entity.setAssistantTurnId(
                toNullableString(run.assistantTurnId())
        );
        entity.setFailureCode(run.failureCode());
        entity.setVersion(run.version());
        entity.setAcceptedAt(run.acceptedAt());
        entity.setStartedAt(run.startedAt());
        entity.setFinishedAt(run.finishedAt());
        entity.setCreatedAt(run.createdAt());
        entity.setUpdatedAt(run.updatedAt());
        return entity;
    }

    public CoachingRun toDomain(CoachingRunEntity entity) {
        Objects.requireNonNull(entity, "entity不能为空");

        return new CoachingRun(
                UUID.fromString(entity.getRunId()),
                new ActorId(entity.getOwnerId()),
                UUID.fromString(entity.getSessionId()),
                UUID.fromString(entity.getRequestId()),
                entity.getRequestFingerprint(),
                requireLong(
                        entity.getExpectedSessionVersion(),
                        "expectedSessionVersion"
                ),
                CoachingRunStatus.valueOf(entity.getRunStatus()),
                toNullableUuid(entity.getUserTurnId()),
                toNullableUuid(entity.getAssistantTurnId()),
                entity.getFailureCode(),
                requireLong(entity.getVersion(), "version"),
                entity.getAcceptedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static String toNullableString(UUID value) {
        return value == null ? null : value.toString();
    }

    private static UUID toNullableUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static long requireLong(
            Long value,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalStateException(
                    "数据库" + fieldName + "不能为空"
            );
        }
        return value;
    }
}