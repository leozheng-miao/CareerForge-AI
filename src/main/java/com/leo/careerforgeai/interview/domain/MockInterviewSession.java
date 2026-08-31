package com.leo.careerforgeai.interview.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存模拟面试身份、冻结输入、生命周期、预算和乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param interviewId 服务端生成的面试UUID
 * @param ownerId 面试所属用户
 * @param requestId 客户端创建面试时生成的幂等UUID
 * @param requestFingerprint 创建请求的规范化小写SHA-256
 * @param mode 面试模式
 * @param inputSnapshotId 冻结输入快照UUID
 * @param inputSnapshotHash 冻结输入快照的小写SHA-256
 * @param status 当前面试状态
 * @param budgetPolicy 服务端预算上限
 * @param failureCode 失败或中断时的稳定错误码
 * @param version 聚合乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 * @param finishedAt 进入终态的时间
 **/
public record MockInterviewSession(
        UUID interviewId,
        ActorId ownerId,
        UUID requestId,
        String requestFingerprint,
        InterviewMode mode,
        UUID inputSnapshotId,
        String inputSnapshotHash,
        InterviewStatus status,
        InterviewBudgetPolicy budgetPolicy,
        InterviewFailureCode failureCode,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {

    private static final Pattern SHA256_PATTERN =
            Pattern.compile("[0-9a-f]{64}");

    public MockInterviewSession {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");
        Objects.requireNonNull(mode, "mode不能为空");
        Objects.requireNonNull(inputSnapshotId, "inputSnapshotId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(budgetPolicy, "budgetPolicy不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");

        requestFingerprint = normalizeSha256(
                requestFingerprint,
                "requestFingerprint"
        );
        inputSnapshotHash = normalizeSha256(
                inputSnapshotHash,
                "inputSnapshotHash"
        );

        if (version < 0) {
            throw new IllegalArgumentException("version不能小于0");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "updatedAt不能早于createdAt"
            );
        }

        validateTerminalFields(
                status,
                failureCode,
                createdAt,
                finishedAt
        );
    }

    public static MockInterviewSession create(
            UUID interviewId,
            ActorId ownerId,
            UUID requestId,
            String requestFingerprint,
            InterviewMode mode,
            UUID inputSnapshotId,
            String inputSnapshotHash,
            InterviewBudgetPolicy budgetPolicy,
            Instant now
    ) {
        Objects.requireNonNull(now, "now不能为空");

        return new MockInterviewSession(
                interviewId,
                ownerId,
                requestId,
                requestFingerprint,
                mode,
                inputSnapshotId,
                inputSnapshotHash,
                InterviewStatus.CREATED,
                budgetPolicy,
                null,
                0,
                now,
                now,
                null
        );
    }

    public MockInterviewSession startQuestionGeneration(Instant now) {
        return transitionTo(
                InterviewStatus.GENERATING_QUESTION,
                null,
                now
        );
    }

    public MockInterviewSession waitForAnswer(Instant now) {
        return transitionTo(
                InterviewStatus.WAITING_FOR_ANSWER,
                null,
                now
        );
    }

    public MockInterviewSession startReview(Instant now) {
        return transitionTo(
                InterviewStatus.REVIEWING,
                null,
                now
        );
    }

    public MockInterviewSession continueQuestioning(Instant now) {
        return transitionTo(
                InterviewStatus.GENERATING_QUESTION,
                null,
                now
        );
    }

    public MockInterviewSession startReportGeneration(Instant now) {
        return transitionTo(
                InterviewStatus.GENERATING_REPORT,
                null,
                now
        );
    }

    public MockInterviewSession awaitConfirmation(Instant now) {
        return transitionTo(
                InterviewStatus.AWAITING_CONFIRMATION,
                null,
                now
        );
    }

    public MockInterviewSession complete(Instant now) {
        return transitionTo(
                InterviewStatus.COMPLETED,
                null,
                now
        );
    }

    public MockInterviewSession fail(
            InterviewFailureCode failureCode,
            Instant now
    ) {
        return transitionTo(
                InterviewStatus.FAILED,
                Objects.requireNonNull(
                        failureCode,
                        "failureCode不能为空"
                ),
                now
        );
    }

    public MockInterviewSession interrupt(
            InterviewFailureCode failureCode,
            Instant now
    ) {
        return transitionTo(
                InterviewStatus.INTERRUPTED,
                Objects.requireNonNull(
                        failureCode,
                        "failureCode不能为空"
                ),
                now
        );
    }

    public MockInterviewSession retryReportGeneration(Instant now) {
        Objects.requireNonNull(now, "now不能为空");
        if (status != InterviewStatus.INTERRUPTED) {
            throw new IllegalStateException("只有INTERRUPTED面试可以重新生成报告");
        }
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("操作时间不能早于面试更新时间");

        return new MockInterviewSession(
                interviewId, ownerId, requestId, requestFingerprint, mode,
                inputSnapshotId, inputSnapshotHash, InterviewStatus.GENERATING_REPORT,
                budgetPolicy, null, nextVersion(), createdAt, now, null
        );
    }

    public MockInterviewSession cancel(Instant now) {
        return transitionTo(
                InterviewStatus.CANCELLED,
                null,
                now
        );
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    private MockInterviewSession transitionTo(
            InterviewStatus targetStatus,
            InterviewFailureCode nextFailureCode,
            Instant now
    ) {
        Objects.requireNonNull(targetStatus, "targetStatus不能为空");
        Objects.requireNonNull(now, "now不能为空");

        if (!status.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    "非法面试状态迁移："
                            + status
                            + " -> "
                            + targetStatus
            );
        }
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "操作时间不能早于面试更新时间"
            );
        }

        return new MockInterviewSession(
                interviewId,
                ownerId,
                requestId,
                requestFingerprint,
                mode,
                inputSnapshotId,
                inputSnapshotHash,
                targetStatus,
                budgetPolicy,
                nextFailureCode,
                nextVersion(),
                createdAt,
                now,
                targetStatus.isTerminal() ? now : null
        );
    }

    private long nextVersion() {
        try {
            return Math.incrementExact(version);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "面试版本超出允许范围",
                    exception
            );
        }
    }

    private static String normalizeSha256(
            String value,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + "不能为空"
            );
        }

        String normalized = value.strip();

        if (!SHA256_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    fieldName + "必须是64位小写SHA-256"
            );
        }
        return normalized;
    }

    private static void validateTerminalFields(
            InterviewStatus status,
            InterviewFailureCode failureCode,
            Instant createdAt,
            Instant finishedAt
    ) {
        boolean requiresFailureCode =
                status == InterviewStatus.FAILED
                        || status == InterviewStatus.INTERRUPTED;

        if (requiresFailureCode != (failureCode != null)) {
            throw new IllegalArgumentException(
                    "failureCode与面试状态不匹配"
            );
        }
        if (status.isTerminal() != (finishedAt != null)) {
            throw new IllegalArgumentException(
                    "finishedAt与面试终态不匹配"
            );
        }
        if (finishedAt != null && finishedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "finishedAt不能早于createdAt"
            );
        }
    }
}