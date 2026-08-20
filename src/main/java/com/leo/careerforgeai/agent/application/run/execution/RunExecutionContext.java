package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 显式携带虚拟线程执行Run所需的owner、标识和Deadline
 * @author: Miao Zheng
 * @date: 2026-08-20
 * @param ownerId Run所属用户
 * @param runId Coaching Run UUID
 * @param traceId 服务端生成的安全Trace标识
 * @param submittedAt Run提交到执行器的时间
 * @param deadline Run允许执行到的最终时间
 **/
public record RunExecutionContext(
        ActorId ownerId,
        UUID runId,
        String traceId,
        Instant submittedAt,
        Instant deadline
) {

    private static final Pattern TRACE_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public RunExecutionContext {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(runId, "runId不能为空");
        Objects.requireNonNull(submittedAt, "submittedAt不能为空");
        Objects.requireNonNull(deadline, "deadline不能为空");

        if (traceId == null || !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            throw new IllegalArgumentException("traceId格式不合法");
        }
        if (!deadline.isAfter(submittedAt)) {
            throw new IllegalArgumentException("deadline必须晚于submittedAt");
        }
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now不能为空");
        return !now.isBefore(deadline);
    }
}