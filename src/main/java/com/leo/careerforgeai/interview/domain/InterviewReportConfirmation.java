package com.leo.careerforgeai.interview.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 聚合一次报告确认请求、逐项用户决定及其下游应用结果
 * @author: Miao Zheng
 * @date: 2026-08-29
 * @param confirmationId 确认单UUID
 * @param reportId 报告UUID
 * @param interviewId 面试UUID
 * @param ownerId 所属用户
 * @param requestId 客户端幂等请求UUID
 * @param requestFingerprint 请求内容的小写SHA-256
 * @param expectedReportVersion 客户端读取报告时的乐观锁版本
 * @param status 确认单应用状态
 * @param decisions 报告建议的逐项决定
 * @param failureCode 确认单失败码
 * @param version 确认单乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param applicationFinishedAt 下游应用完成时间
 */
public record InterviewReportConfirmation(
        UUID confirmationId,
        UUID reportId,
        UUID interviewId,
        ActorId ownerId,
        UUID requestId,
        String requestFingerprint,
        long expectedReportVersion,
        Status status,
        List<Decision> decisions,
        String failureCode,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant applicationFinishedAt
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public InterviewReportConfirmation {
        Objects.requireNonNull(confirmationId, "confirmationId不能为空");
        Objects.requireNonNull(reportId, "reportId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");
        requestFingerprint = requireSha256(requestFingerprint, "requestFingerprint");
        if (expectedReportVersion < 0) throw new IllegalArgumentException("expectedReportVersion不能小于0");
        if (version < 0) throw new IllegalArgumentException("version不能小于0");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt不能早于createdAt");
        decisions = requireDecisions(decisions, confirmationId, reportId, interviewId, ownerId);
        validateLifecycle(status, decisions, failureCode, createdAt, applicationFinishedAt);
    }

    public static InterviewReportConfirmation pendingApplication(
            UUID confirmationId,
            UUID reportId,
            UUID interviewId,
            ActorId ownerId,
            UUID requestId,
            String requestFingerprint,
            long expectedReportVersion,
            List<Decision> decisions,
            Instant now
    ) {
        Objects.requireNonNull(now, "now不能为空");
        return new InterviewReportConfirmation(
                confirmationId,
                reportId,
                interviewId,
                ownerId,
                requestId,
                requestFingerprint,
                expectedReportVersion,
                Status.PENDING_APPLICATION,
                decisions,
                null,
                0,
                now,
                now,
                null
        );
    }

    public InterviewReportConfirmation recordDecision(Decision updatedDecision) {
        Objects.requireNonNull(updatedDecision, "updatedDecision不能为空");
        if (status != Status.PENDING_APPLICATION) {
            throw new IllegalStateException("只有PENDING_APPLICATION确认单可以记录应用结果");
        }

        int targetIndex = -1;
        for (int index = 0; index < decisions.size(); index++) {
            if (decisions.get(index).decisionId().equals(updatedDecision.decisionId())) {
                targetIndex = index;
                break;
            }
        }
        if (targetIndex < 0) throw new IllegalArgumentException("updatedDecision不属于当前确认单");

        Decision current = decisions.get(targetIndex);
        requireSameIdentity(current, updatedDecision);
        if (current.equals(updatedDecision)) return this;
        if (current.applicationStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("已经结束的决定不能再次更新");
        }
        if (updatedDecision.applicationStatus() != ApplicationStatus.APPLIED
                && updatedDecision.applicationStatus() != ApplicationStatus.FAILED) {
            throw new IllegalArgumentException("确认建议只能更新为APPLIED或FAILED");
        }
        if (updatedDecision.updatedAt().isBefore(updatedAt)) {
            throw new IllegalArgumentException("决定更新时间不能早于确认单更新时间");
        }

        List<Decision> updatedDecisions = new java.util.ArrayList<>(decisions);
        updatedDecisions.set(targetIndex, updatedDecision);
        return new InterviewReportConfirmation(
                confirmationId,
                reportId,
                interviewId,
                ownerId,
                requestId,
                requestFingerprint,
                expectedReportVersion,
                status,
                updatedDecisions,
                null,
                nextVersion(),
                createdAt,
                updatedDecision.updatedAt(),
                null
        );
    }

    public InterviewReportConfirmation finish(String aggregateFailureCode, Instant now) {
        Objects.requireNonNull(now, "now不能为空");
        if (status != Status.PENDING_APPLICATION) return this;
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("完成时间不能早于确认单更新时间");
        if (decisions.stream().anyMatch(decision -> decision.applicationStatus() == ApplicationStatus.PENDING)) {
            throw new IllegalStateException("仍有建议等待应用，不能结束确认单");
        }

        long appliedCount = decisions.stream()
                .filter(decision -> decision.applicationStatus() == ApplicationStatus.APPLIED)
                .count();
        long failedCount = decisions.stream()
                .filter(decision -> decision.applicationStatus() == ApplicationStatus.FAILED)
                .count();

        Status completedStatus;
        if (failedCount == 0) {
            completedStatus = Status.APPLIED;
            if (aggregateFailureCode != null) {
                throw new IllegalArgumentException("成功确认单不能包含failureCode");
            }
        } else {
            requireText(aggregateFailureCode, "aggregateFailureCode", 64);
            completedStatus = appliedCount > 0 ? Status.PARTIALLY_APPLIED : Status.FAILED;
        }

        return new InterviewReportConfirmation(
                confirmationId,
                reportId,
                interviewId,
                ownerId,
                requestId,
                requestFingerprint,
                expectedReportVersion,
                completedStatus,
                decisions,
                aggregateFailureCode,
                nextVersion(),
                createdAt,
                now,
                now
        );
    }

    private long nextVersion() {
        try {
            return Math.incrementExact(version);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("确认单版本超出允许范围", exception);
        }
    }

    private static List<Decision> requireDecisions(
            List<Decision> decisions,
            UUID confirmationId,
            UUID reportId,
            UUID interviewId,
            ActorId ownerId
    ) {
        if (decisions == null || decisions.size() > 20) {
            throw new IllegalArgumentException("decisions数量不能超过20");
        }
        List<Decision> copy = List.copyOf(decisions);
        Set<UUID> decisionIds = new HashSet<>();
        Set<UUID> suggestionIds = new HashSet<>();
        for (Decision decision : copy) {
            Objects.requireNonNull(decision, "decision不能为空");
            if (!decision.confirmationId().equals(confirmationId)
                    || !decision.reportId().equals(reportId)
                    || !decision.interviewId().equals(interviewId)
                    || !decision.ownerId().equals(ownerId)) {
                throw new IllegalArgumentException("decision与确认单作用域不一致");
            }
            if (!decisionIds.add(decision.decisionId())) {
                throw new IllegalArgumentException("decisionId不能重复");
            }
            if (!suggestionIds.add(decision.suggestionId())) {
                throw new IllegalArgumentException("同一建议只能决定一次");
            }
        }
        return copy;
    }

    private static void validateLifecycle(
            Status status,
            List<Decision> decisions,
            String failureCode,
            Instant createdAt,
            Instant finishedAt
    ) {
        boolean pendingDecision = decisions.stream()
                .anyMatch(decision -> decision.applicationStatus() == ApplicationStatus.PENDING);
        long appliedCount = decisions.stream()
                .filter(decision -> decision.applicationStatus() == ApplicationStatus.APPLIED)
                .count();
        long failedCount = decisions.stream()
                .filter(decision -> decision.applicationStatus() == ApplicationStatus.FAILED)
                .count();

        switch (status) {
            case PENDING_APPLICATION -> {
                if (failureCode != null || finishedAt != null) {
                    throw new IllegalArgumentException("PENDING_APPLICATION不能包含失败码或完成时间");
                }
            }
            case APPLIED -> {
                if (pendingDecision || failedCount > 0 || failureCode != null || finishedAt == null) {
                    throw new IllegalArgumentException("APPLIED确认单生命周期不合法");
                }
            }
            case PARTIALLY_APPLIED -> {
                if (pendingDecision || appliedCount == 0 || failedCount == 0
                        || failureCode == null || finishedAt == null) {
                    throw new IllegalArgumentException("PARTIALLY_APPLIED确认单生命周期不合法");
                }
            }
            case FAILED -> {
                if (pendingDecision || appliedCount > 0 || failedCount == 0
                        || failureCode == null || finishedAt == null) {
                    throw new IllegalArgumentException("FAILED确认单生命周期不合法");
                }
            }
        }

        if (failureCode != null) requireText(failureCode, "failureCode", 64);
        if (finishedAt != null && finishedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("applicationFinishedAt不能早于createdAt");
        }
    }

    private static void requireSameIdentity(Decision current, Decision updated) {
        if (!current.confirmationId().equals(updated.confirmationId())
                || !current.suggestionId().equals(updated.suggestionId())
                || !current.reportId().equals(updated.reportId())
                || !current.interviewId().equals(updated.interviewId())
                || !current.ownerId().equals(updated.ownerId())
                || current.decisionType() != updated.decisionType()
                || !current.createdAt().equals(updated.createdAt())) {
            throw new IllegalArgumentException("决定更新改变了不可变身份");
        }
    }

    private static String requireSha256(String value, String fieldName) {
        if (value == null || !SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "必须是64位小写SHA-256");
        }
        return value;
    }

    private static void requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能为空且长度不能超过" + maxLength);
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义整张确认单的下游应用结果
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    public enum Status {
        PENDING_APPLICATION,
        APPLIED,
        PARTIALLY_APPLIED,
        FAILED
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义用户对单条报告建议的决定
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    public enum DecisionType {
        CONFIRMED,
        REJECTED
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义单条建议的下游应用状态
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    public enum ApplicationStatus {
        PENDING,
        APPLIED,
        REJECTED,
        FAILED
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存用户对单条报告建议的决定及其下游应用结果
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param decisionId 决定UUID
     * @param confirmationId 确认单UUID
     * @param suggestionId 报告建议UUID
     * @param reportId 报告UUID
     * @param interviewId 面试UUID
     * @param ownerId 所属用户
     * @param decisionType 用户决定类型
     * @param applicationStatus 下游应用状态
     * @param outputReferenceId 成功写入的Memory或TrainingPlan引用UUID
     * @param failureCode 应用失败码
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param finishedAt 应用完成时间
     */
    public record Decision(
            UUID decisionId,
            UUID confirmationId,
            UUID suggestionId,
            UUID reportId,
            UUID interviewId,
            ActorId ownerId,
            DecisionType decisionType,
            ApplicationStatus applicationStatus,
            UUID outputReferenceId,
            String failureCode,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt
    ) {

        public Decision {
            Objects.requireNonNull(decisionId, "decisionId不能为空");
            Objects.requireNonNull(confirmationId, "confirmationId不能为空");
            Objects.requireNonNull(suggestionId, "suggestionId不能为空");
            Objects.requireNonNull(reportId, "reportId不能为空");
            Objects.requireNonNull(interviewId, "interviewId不能为空");
            Objects.requireNonNull(ownerId, "ownerId不能为空");
            Objects.requireNonNull(decisionType, "decisionType不能为空");
            Objects.requireNonNull(applicationStatus, "applicationStatus不能为空");
            Objects.requireNonNull(createdAt, "createdAt不能为空");
            Objects.requireNonNull(updatedAt, "updatedAt不能为空");
            if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt不能早于createdAt");
            validateDecisionLifecycle(
                    decisionType, applicationStatus, outputReferenceId,
                    failureCode, createdAt, finishedAt
            );
        }

        public static Decision confirmed(
                UUID decisionId,
                UUID confirmationId,
                UUID suggestionId,
                UUID reportId,
                UUID interviewId,
                ActorId ownerId,
                Instant now
        ) {
            return new Decision(
                    decisionId, confirmationId, suggestionId, reportId, interviewId, ownerId,
                    DecisionType.CONFIRMED, ApplicationStatus.PENDING,
                    null, null, now, now, null
            );
        }

        public static Decision rejected(
                UUID decisionId,
                UUID confirmationId,
                UUID suggestionId,
                UUID reportId,
                UUID interviewId,
                ActorId ownerId,
                Instant now
        ) {
            return new Decision(
                    decisionId, confirmationId, suggestionId, reportId, interviewId, ownerId,
                    DecisionType.REJECTED, ApplicationStatus.REJECTED,
                    null, null, now, now, now
            );
        }

        public Decision applied(UUID outputReferenceId, Instant now) {
            Objects.requireNonNull(outputReferenceId, "outputReferenceId不能为空");
            requirePending(now);
            return new Decision(
                    decisionId, confirmationId, suggestionId, reportId, interviewId, ownerId,
                    decisionType, ApplicationStatus.APPLIED,
                    outputReferenceId, null, createdAt, now, now
            );
        }

        public Decision failed(String failureCode, Instant now) {
            requireText(failureCode, "failureCode", 64);
            requirePending(now);
            return new Decision(
                    decisionId, confirmationId, suggestionId, reportId, interviewId, ownerId,
                    decisionType, ApplicationStatus.FAILED,
                    null, failureCode, createdAt, now, now
            );
        }

        private void requirePending(Instant now) {
            Objects.requireNonNull(now, "now不能为空");
            if (decisionType != DecisionType.CONFIRMED
                    || applicationStatus != ApplicationStatus.PENDING) {
                throw new IllegalStateException("只有已确认且PENDING的建议可以应用");
            }
            if (now.isBefore(updatedAt)) throw new IllegalArgumentException("应用时间不能早于更新时间");
        }

        private static void validateDecisionLifecycle(
                DecisionType decisionType,
                ApplicationStatus status,
                UUID outputReferenceId,
                String failureCode,
                Instant createdAt,
                Instant finishedAt
        ) {
            if (decisionType == DecisionType.REJECTED) {
                if (status != ApplicationStatus.REJECTED || outputReferenceId != null
                        || failureCode != null || finishedAt == null) {
                    throw new IllegalArgumentException("REJECTED决定生命周期不合法");
                }
            } else {
                switch (status) {
                    case PENDING -> {
                        if (outputReferenceId != null || failureCode != null || finishedAt != null) {
                            throw new IllegalArgumentException("PENDING决定生命周期不合法");
                        }
                    }
                    case APPLIED -> {
                        if (outputReferenceId == null || failureCode != null || finishedAt == null) {
                            throw new IllegalArgumentException("APPLIED决定生命周期不合法");
                        }
                    }
                    case FAILED -> {
                        if (outputReferenceId != null || failureCode == null || finishedAt == null) {
                            throw new IllegalArgumentException("FAILED决定生命周期不合法");
                        }
                    }
                    case REJECTED -> throw new IllegalArgumentException("CONFIRMED决定不能进入REJECTED状态");
                }
            }

            if (failureCode != null) requireText(failureCode, "failureCode", 64);
            if (finishedAt != null && finishedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("finishedAt不能早于createdAt");
            }
        }
    }
}