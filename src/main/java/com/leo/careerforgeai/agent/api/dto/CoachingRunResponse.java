package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回不包含owner和请求指纹的安全Coaching Run事实
 * @author: Miao Zheng
 * @date: 2026-08-20
 * @param runId Run UUID
 * @param sessionId Run所属Session
 * @param requestId 客户端幂等请求UUID
 * @param expectedSessionVersion Run创建时预期的Session版本
 * @param status 当前Run状态
 * @param userTurnId 已保存的USER Turn
 * @param assistantTurnId 已保存的ASSISTANT Turn
 * @param failureCode 受控失败码
 * @param version Run乐观锁版本
 * @param acceptedAt Run接受时间
 * @param startedAt Run开始执行时间
 * @param finishedAt Run终结时间
 * @param createdAt Run创建时间
 * @param updatedAt Run更新时间
 **/
public record CoachingRunResponse(
        UUID runId,
        UUID sessionId,
        UUID requestId,
        long expectedSessionVersion,
        CoachingRunStatus status,
        UUID userTurnId,
        UUID assistantTurnId,
        String failureCode,
        long version,
        Instant acceptedAt,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public CoachingRunResponse {
        Objects.requireNonNull(runId, "runId不能为空");
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");
    }

    public static CoachingRunResponse from(CoachingRun run) {
        Objects.requireNonNull(run, "run不能为空");
        return new CoachingRunResponse(
                run.runId(),
                run.sessionId(),
                run.requestId(),
                run.expectedSessionVersion(),
                run.status(),
                run.userTurnId(),
                run.assistantTurnId(),
                run.failureCode(),
                run.version(),
                run.acceptedAt(),
                run.startedAt(),
                run.finishedAt(),
                run.createdAt(),
                run.updatedAt()
        );
    }
}