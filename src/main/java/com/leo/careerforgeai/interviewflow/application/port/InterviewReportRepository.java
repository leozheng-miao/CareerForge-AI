package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义一场面试一份报告的owner查询和CAS持久化端口
 * @author: Miao Zheng
 * @date: 2026-10-02
 */
public interface InterviewReportRepository {

    void insert(ReportSnapshot report);

    Optional<ReportSnapshot> findByInterview(
            ActorId ownerId,
            UUID interviewId
    );

    boolean updateIfVersionMatches(
            ActorId ownerId,
            ReportSnapshot updated,
            long expectedVersion
    );

    /**
     * @param reportId 报告ID
     * @param interviewId 面试会话ID
     * @param ownerId 面试所属用户
     * @param status 报告生成状态
     * @param reportJson 已校验的结构化报告JSON
     * @param schemaVersion 报告Schema版本
     * @param inputVersion 报告依据的输入版本
     * @param planVersion 报告依据的计划版本
     * @param contextFingerprint 报告依据的上下文指纹
     * @param failureCode 生成失败原因码
     * @param version 持久化乐观锁版本
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param completedAt 成功或失败终结时间
     */
    record ReportSnapshot(
            UUID reportId,
            UUID interviewId,
            ActorId ownerId,
            ReportStatus status,
            String reportJson,
            int schemaVersion,
            long inputVersion,
            long planVersion,
            String contextFingerprint,
            String failureCode,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {

        public ReportSnapshot {
            Objects.requireNonNull(reportId, "reportId不能为空");
            Objects.requireNonNull(interviewId, "interviewId不能为空");
            Objects.requireNonNull(ownerId, "ownerId不能为空");
            Objects.requireNonNull(status, "status不能为空");
            Objects.requireNonNull(createdAt, "createdAt不能为空");
            Objects.requireNonNull(updatedAt, "updatedAt不能为空");
            if (schemaVersion < 1 || inputVersion < 1 || planVersion < 1) {
                throw new IllegalArgumentException("报告版本必须大于等于1");
            }
            if (version < 0) {
                throw new IllegalArgumentException("version不能小于0");
            }
            if (contextFingerprint == null
                    || contextFingerprint.length() != 64) {
                throw new IllegalArgumentException(
                        "contextFingerprint必须为64位"
                );
            }
            if (updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("updatedAt不能早于createdAt");
            }
            validateLifecycle(
                    status,
                    reportJson,
                    failureCode,
                    completedAt,
                    createdAt
            );
        }

        private static void validateLifecycle(
                ReportStatus status,
                String reportJson,
                String failureCode,
                Instant completedAt,
                Instant createdAt
        ) {
            if (status == ReportStatus.READY) {
                if (reportJson == null || completedAt == null
                        || failureCode != null) {
                    throw new IllegalArgumentException("就绪报告事实不完整");
                }
            } else if (status == ReportStatus.FAILED) {
                if (reportJson != null || completedAt == null
                        || failureCode == null || failureCode.isBlank()) {
                    throw new IllegalArgumentException("失败报告事实不完整");
                }
            } else if (reportJson != null || completedAt != null
                    || failureCode != null) {
                throw new IllegalArgumentException("未终结报告不能包含终态事实");
            }
            if (completedAt != null && completedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException(
                        "completedAt不能早于createdAt"
                );
            }
        }
    }

    enum ReportStatus {
        PENDING,
        GENERATING,
        READY,
        FAILED
    }
}
