package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义面试后台任务幂等入队和过期租约原子认领端口
 * @author: Miao Zheng
 * @date: 2026-10-02
 */
public interface InterviewTaskRepository {

    boolean enqueue(TaskDefinition task);

    Optional<ClaimedTask> claimNextAvailableTask(
            Instant now,
            String leaseOwner,
            Instant leaseUntil
    );

    default boolean markSucceeded(ClaimedTask task, Instant completedAt) {
        return false;
    }

    default boolean markRetryWait(
            ClaimedTask task,
            Instant nextRetryAt,
            String errorCode,
            String errorMessage,
            Instant updatedAt
    ) {
        return false;
    }

    /**
     * 将已认领但尚未执行的任务放回等待队列，并撤销本次认领增加的尝试次数。
     */
    default boolean rescheduleUnstarted(
            ClaimedTask task,
            Instant nextRetryAt,
            String errorCode,
            String errorMessage,
            Instant updatedAt
    ) {
        return false;
    }

    default boolean markFailed(
            ClaimedTask task,
            String errorCode,
            String errorMessage,
            Instant completedAt
    ) {
        return false;
    }

    /**
     * @param taskId 任务ID
     * @param interviewId 面试会话ID
     * @param ownerId 面试所属用户
     * @param taskType 受限任务类型
     * @param businessKey 幂等业务键
     * @param maxAttempts 最大尝试次数
     * @param inputVersion 任务创建时的输入版本
     * @param planVersion 任务创建时的计划版本
     * @param contextFingerprint 拒绝陈旧结果的上下文指纹
     * @param createdAt 创建时间
     */
    record TaskDefinition(
            UUID taskId,
            UUID interviewId,
            ActorId ownerId,
            TaskType taskType,
            String businessKey,
            int maxAttempts,
            long inputVersion,
            long planVersion,
            String contextFingerprint,
            Instant createdAt
    ) {

        public TaskDefinition {
            Objects.requireNonNull(taskId, "taskId不能为空");
            Objects.requireNonNull(interviewId, "interviewId不能为空");
            Objects.requireNonNull(ownerId, "ownerId不能为空");
            Objects.requireNonNull(taskType, "taskType不能为空");
            Objects.requireNonNull(createdAt, "createdAt不能为空");
            if (businessKey == null || businessKey.isBlank()) {
                throw new IllegalArgumentException("businessKey不能为空");
            }
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts必须大于等于1");
            }
            if (inputVersion < 1 || planVersion < 1) {
                throw new IllegalArgumentException("任务版本必须大于等于1");
            }
            if (contextFingerprint == null
                    || contextFingerprint.length() != 64) {
                throw new IllegalArgumentException(
                        "contextFingerprint必须为64位"
                );
            }
        }
    }

    /**
     * @param taskId 已认领任务ID
     * @param interviewId 面试会话ID
     * @param ownerId 面试所属用户
     * @param taskType 任务类型
     * @param businessKey 幂等业务键
     * @param attemptCount 本次认领后的尝试次数
     * @param maxAttempts 最大尝试次数
     * @param inputVersion 任务创建时的输入版本
     * @param planVersion 任务创建时的计划版本
     * @param contextFingerprint 拒绝陈旧结果的上下文指纹
     * @param version 持久化乐观锁版本
     * @param leaseOwner 本次租约持有者
     * @param leaseUntil 本次租约到期时间
     */
    record ClaimedTask(
            UUID taskId,
            UUID interviewId,
            ActorId ownerId,
            TaskType taskType,
            String businessKey,
            int attemptCount,
            int maxAttempts,
            long inputVersion,
            long planVersion,
            String contextFingerprint,
            long version,
            String leaseOwner,
            Instant leaseUntil
    ) {

        public ClaimedTask {
            Objects.requireNonNull(taskId, "taskId不能为空");
            Objects.requireNonNull(interviewId, "interviewId不能为空");
            Objects.requireNonNull(ownerId, "ownerId不能为空");
            Objects.requireNonNull(taskType, "taskType不能为空");
            Objects.requireNonNull(leaseUntil, "leaseUntil不能为空");
            if (businessKey == null || businessKey.isBlank()) {
                throw new IllegalArgumentException("businessKey不能为空");
            }
            if (attemptCount < 1) {
                throw new IllegalArgumentException("attemptCount必须大于等于1");
            }
            if (maxAttempts < attemptCount) {
                throw new IllegalArgumentException(
                        "maxAttempts不能小于attemptCount"
                );
            }
            if (inputVersion < 1 || planVersion < 1 || version < 0) {
                throw new IllegalArgumentException("已认领任务版本不合法");
            }
            if (contextFingerprint == null
                    || contextFingerprint.length() != 64) {
                throw new IllegalArgumentException(
                        "contextFingerprint必须为64位"
                );
            }
            if (leaseOwner == null || leaseOwner.isBlank()) {
                throw new IllegalArgumentException("leaseOwner不能为空");
            }
        }
    }

    enum TaskType {
        PREPARE_INTERVIEW,
        ANALYZE_TURN,
        GENERATE_REPORT
    }
}
