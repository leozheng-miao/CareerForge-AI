package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义面试输入快照的owner隔离查询和CAS持久化端口
 * @author: Miao Zheng
 * @date: 2026-10-02
 */
public interface InterviewInputSnapshotRepository {

    void insert(InputSnapshot snapshot);

    Optional<InputSnapshot> findBySession(
            ActorId ownerId,
            UUID interviewId
    );

    boolean updateIfVersionMatches(
            ActorId ownerId,
            InputSnapshot updated,
            long expectedVersion
    );

    /**
     * @param inputSnapshotId 输入快照ID
     * @param interviewId 面试会话ID
     * @param ownerId 面试所属用户
     * @param direction 用户选择的岗位方向
     * @param jobDescription 综合面试JD，快速面试为null
     * @param salaryRange 综合面试薪资范围，快速面试为null
     * @param personalDescription 用户可选个人描述
     * @param confirmedProfileJson 用户确认的结构化画像JSON
     * @param profileSchemaVersion 确认画像Schema版本
     * @param difficulty 综合面试难度，快速面试为null
     * @param durationSeconds 有效作答时长配置
     * @param inputVersion 输入业务版本
     * @param version 持久化乐观锁版本
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    record InputSnapshot(
            UUID inputSnapshotId,
            UUID interviewId,
            ActorId ownerId,
            String direction,
            String jobDescription,
            String salaryRange,
            String personalDescription,
            String confirmedProfileJson,
            Integer profileSchemaVersion,
            String difficulty,
            long durationSeconds,
            long inputVersion,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {

        private static final Set<String> DIFFICULTIES = Set.of(
                "BASIC",
                "STANDARD",
                "CHALLENGE"
        );

        public InputSnapshot {
            Objects.requireNonNull(inputSnapshotId, "inputSnapshotId不能为空");
            Objects.requireNonNull(interviewId, "interviewId不能为空");
            Objects.requireNonNull(ownerId, "ownerId不能为空");
            Objects.requireNonNull(createdAt, "createdAt不能为空");
            Objects.requireNonNull(updatedAt, "updatedAt不能为空");
            if (direction == null || direction.isBlank()) {
                throw new IllegalArgumentException("direction不能为空");
            }
            if (durationSeconds != 3600 && durationSeconds != 5400) {
                throw new IllegalArgumentException(
                        "durationSeconds只能为3600或5400"
                );
            }
            if (inputVersion < 1 || version < 0) {
                throw new IllegalArgumentException("快照版本不合法");
            }
            if (updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("updatedAt不能早于createdAt");
            }
            if ((confirmedProfileJson == null)
                    != (profileSchemaVersion == null)) {
                throw new IllegalArgumentException(
                        "确认画像与Schema版本必须同时存在"
                );
            }
            if (profileSchemaVersion != null && profileSchemaVersion < 1) {
                throw new IllegalArgumentException(
                        "profileSchemaVersion必须大于等于1"
                );
            }
            if (difficulty != null && !DIFFICULTIES.contains(difficulty)) {
                throw new IllegalArgumentException("difficulty不合法");
            }
        }
    }
}
