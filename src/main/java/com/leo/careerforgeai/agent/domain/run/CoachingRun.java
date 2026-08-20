package com.leo.careerforgeai.agent.domain.run;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存一次Career Coach请求的耐久身份、状态、Turn引用和乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-08-20
 * @param runId 服务端生成的Run UUID
 * @param ownerId Run所属用户
 * @param sessionId Run所属会话UUID
 * @param requestId 客户端生成的幂等请求UUID
 * @param requestFingerprint 规范化请求的小写SHA-256
 * @param expectedSessionVersion 客户端提交时预期的Session版本
 * @param status 当前Run状态
 * @param userTurnId 已保存的USER Turn，尚未接受时为空
 * @param assistantTurnId 已保存的ASSISTANT Turn，尚未终结时为空
 * @param failureCode 受控失败码，成功或非终态时为空
 * @param version RunC聚合乐观锁版本
 * @param acceptedAt Run通过准入并保存USER Turn的时间
 * @param startedAt Run开始执行Career Coach的时间
 * @param finishedAt Run进入终态的时间
 * @param createdAt Run幂等身份被认领的时间
 * @param updatedAt Run最后更新时间
 **/
public record CoachingRun(
        UUID runId,
        ActorId ownerId,
        UUID sessionId,
        UUID requestId,
        String requestFingerprint,
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

    private static final Pattern FINGERPRINT_PATTERN =
            Pattern.compile("[0-9a-f]{64}");
    private static final Pattern FAILURE_CODE_PATTERN =
            Pattern.compile("[A-Z0-9_]{1,64}");

    public CoachingRun {
        Objects.requireNonNull(runId, "runId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");

        requestFingerprint = normalizeFingerprint(requestFingerprint);
        failureCode = normalizeFailureCode(failureCode);

        if (expectedSessionVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedSessionVersion不能小于0"
            );
        }
        if (version < 0) {
            throw new IllegalArgumentException("version不能小于0");
        }
        if (assistantTurnId != null
                && assistantTurnId.equals(userTurnId)) {
            throw new IllegalArgumentException(
                    "userTurnId和assistantTurnId不能相同"
            );
        }

        validateTimes(
                createdAt,
                updatedAt,
                acceptedAt,
                startedAt,
                finishedAt
        );
        validateLifecycle(
                status,
                userTurnId,
                assistantTurnId,
                failureCode,
                acceptedAt,
                startedAt,
                finishedAt
        );
    }

    public static CoachingRun receive(
            UUID runId,
            ActorId ownerId,
            UUID sessionId,
            UUID requestId,
            String requestFingerprint,
            long expectedSessionVersion,
            Instant now
    ) {
        return new CoachingRun(
                runId,
                ownerId,
                sessionId,
                requestId,
                requestFingerprint,
                expectedSessionVersion,
                CoachingRunStatus.RECEIVED,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                now,
                now
        );
    }

    public CoachingRun accept(UUID userTurnId, Instant now) {
        return transitionTo(
                CoachingRunStatus.ACCEPTED,
                Objects.requireNonNull(userTurnId, "userTurnId不能为空"),
                null,
                null,
                now,
                null,
                null,
                now
        );
    }

    public CoachingRun start(Instant now) {
        return transitionTo(
                CoachingRunStatus.RUNNING,
                userTurnId,
                null,
                null,
                acceptedAt,
                now,
                null,
                now
        );
    }

    public CoachingRun succeed(
            UUID assistantTurnId,
            Instant now
    ) {
        return transitionTo(
                CoachingRunStatus.SUCCEEDED,
                userTurnId,
                Objects.requireNonNull(
                        assistantTurnId,
                        "assistantTurnId不能为空"
                ),
                null,
                acceptedAt,
                startedAt,
                now,
                now
        );
    }

    public CoachingRun fail(
            UUID assistantTurnId,
            String failureCode,
            Instant now
    ) {
        return finishFailure(
                CoachingRunStatus.FAILED,
                assistantTurnId,
                failureCode,
                now
        );
    }

    public CoachingRun timeOut(
            UUID assistantTurnId,
            String failureCode,
            Instant now
    ) {
        return finishFailure(
                CoachingRunStatus.TIMED_OUT,
                assistantTurnId,
                failureCode,
                now
        );
    }

    public CoachingRun reject(
            String failureCode,
            Instant now
    ) {
        return transitionTo(
                CoachingRunStatus.REJECTED,
                userTurnId,
                null,
                failureCode,
                acceptedAt,
                null,
                now,
                now
        );
    }

    public CoachingRun interrupt(
            String failureCode,
            Instant now
    ) {
        return transitionTo(
                CoachingRunStatus.INTERRUPTED,
                userTurnId,
                null,
                failureCode,
                acceptedAt,
                startedAt,
                now,
                now
        );
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    private CoachingRun finishFailure(
            CoachingRunStatus targetStatus,
            UUID assistantTurnId,
            String failureCode,
            Instant now
    ) {
        return transitionTo(
                targetStatus,
                userTurnId,
                Objects.requireNonNull(
                        assistantTurnId,
                        "assistantTurnId不能为空"
                ),
                failureCode,
                acceptedAt,
                startedAt,
                now,
                now
        );
    }

    private CoachingRun transitionTo(
            CoachingRunStatus targetStatus,
            UUID nextUserTurnId,
            UUID nextAssistantTurnId,
            String nextFailureCode,
            Instant nextAcceptedAt,
            Instant nextStartedAt,
            Instant nextFinishedAt,
            Instant now
    ) {
        Objects.requireNonNull(now, "now不能为空");

        if (!status.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    "非法Run状态迁移："
                            + status
                            + " -> "
                            + targetStatus
            );
        }
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "操作时间不能早于Run更新时间"
            );
        }

        return new CoachingRun(
                runId,
                ownerId,
                sessionId,
                requestId,
                requestFingerprint,
                expectedSessionVersion,
                targetStatus,
                nextUserTurnId,
                nextAssistantTurnId,
                nextFailureCode,
                nextVersion(),
                nextAcceptedAt,
                nextStartedAt,
                nextFinishedAt,
                createdAt,
                now
        );
    }

    private long nextVersion() {
        try {
            return Math.incrementExact(version);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Run版本超出允许范围",
                    exception
            );
        }
    }

    private static String normalizeFingerprint(String fingerprint) {
        if (fingerprint == null) {
            throw new IllegalArgumentException(
                    "requestFingerprint不能为空"
            );
        }

        String normalized = fingerprint.strip();

        if (!FINGERPRINT_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "requestFingerprint必须是64位小写SHA-256"
            );
        }
        return normalized;
    }

    private static String normalizeFailureCode(String failureCode) {
        if (failureCode == null) return null;

        String normalized = failureCode.strip();

        if (!FAILURE_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "failureCode格式不合法"
            );
        }
        return normalized;
    }

    private static void validateTimes(
            Instant createdAt,
            Instant updatedAt,
            Instant acceptedAt,
            Instant startedAt,
            Instant finishedAt
    ) {
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "updatedAt不能早于createdAt"
            );
        }
        if (acceptedAt != null && acceptedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "acceptedAt不能早于createdAt"
            );
        }
        if (startedAt != null
                && (
                acceptedAt == null
                        || startedAt.isBefore(acceptedAt)
        )) {
            throw new IllegalArgumentException(
                    "startedAt不能早于acceptedAt"
            );
        }

        Instant finishLowerBound = startedAt != null
                ? startedAt
                : acceptedAt != null ? acceptedAt : createdAt;

        if (finishedAt != null
                && finishedAt.isBefore(finishLowerBound)) {
            throw new IllegalArgumentException(
                    "finishedAt早于允许时间"
            );
        }
    }

    private static void validateLifecycle(
            CoachingRunStatus status,
            UUID userTurnId,
            UUID assistantTurnId,
            String failureCode,
            Instant acceptedAt,
            Instant startedAt,
            Instant finishedAt
    ) {
        switch (status) {
            case RECEIVED -> require(
                    userTurnId == null
                            && assistantTurnId == null
                            && failureCode == null
                            && acceptedAt == null
                            && startedAt == null
                            && finishedAt == null,
                    "RECEIVED状态字段不合法"
            );
            case ACCEPTED -> require(
                    userTurnId != null
                            && assistantTurnId == null
                            && failureCode == null
                            && acceptedAt != null
                            && startedAt == null
                            && finishedAt == null,
                    "ACCEPTED状态字段不合法"
            );
            case RUNNING -> require(
                    userTurnId != null
                            && assistantTurnId == null
                            && failureCode == null
                            && acceptedAt != null
                            && startedAt != null
                            && finishedAt == null,
                    "RUNNING状态字段不合法"
            );
            case SUCCEEDED -> require(
                    userTurnId != null
                            && assistantTurnId != null
                            && failureCode == null
                            && acceptedAt != null
                            && startedAt != null
                            && finishedAt != null,
                    "SUCCEEDED状态字段不合法"
            );
            case FAILED, TIMED_OUT -> require(
                    userTurnId != null
                            && assistantTurnId != null
                            && failureCode != null
                            && acceptedAt != null
                            && startedAt != null
                            && finishedAt != null,
                    status + "状态字段不合法"
            );
            case REJECTED -> require(
                    assistantTurnId == null
                            && failureCode != null
                            && startedAt == null
                            && finishedAt != null
                            && (
                            userTurnId == null
                                    && acceptedAt == null
                            || userTurnId != null
                                    && acceptedAt != null
                    ),
                    "REJECTED状态字段不合法"
            );
            case INTERRUPTED -> require(
                    assistantTurnId == null
                            && failureCode != null
                            && finishedAt != null
                            && (
                            userTurnId == null
                                    && acceptedAt == null
                                    && startedAt == null
                            || userTurnId != null
                                    && acceptedAt != null
                    ),
                    "INTERRUPTED状态字段不合法"
            );
        }
    }

    private static void require(
            boolean condition,
            String message
    ) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}