package com.leo.careerforgeai.interview.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存模拟面试创建时冻结的岗位、Gap、训练计划和个人证据版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param inputSnapshotId 输入快照UUID
 * @param ownerId 快照所属用户
 * @param schemaVersion 快照结构版本
 * @param targetRoleId 已确认目标岗位UUID
 * @param targetRoleVersion 目标岗位业务版本
 * @param skillGapSnapshotId 可选能力差距快照UUID
 * @param trainingPlanId 可选训练计划UUID
 * @param trainingPlanVersion 可选训练计划业务版本
 * @param snapshotContextJson 规范化后的快照上下文JSON
 * @param artifactReferences 冻结的个人证据版本引用
 * @param snapshotHash 完整快照的小写SHA-256
 * @param createdAt 创建时间
 **/
public record MockInterviewInputSnapshot(
        UUID inputSnapshotId,
        ActorId ownerId,
        int schemaVersion,
        UUID targetRoleId,
        long targetRoleVersion,
        UUID skillGapSnapshotId,
        UUID trainingPlanId,
        Long trainingPlanVersion,
        String snapshotContextJson,
        List<ArtifactReference> artifactReferences,
        String snapshotHash,
        Instant createdAt
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public MockInterviewInputSnapshot {
        Objects.requireNonNull(inputSnapshotId, "inputSnapshotId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(targetRoleId, "targetRoleId不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");

        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的输入快照schemaVersion: " + schemaVersion);
        }
        if (targetRoleVersion < 1) {
            throw new IllegalArgumentException("targetRoleVersion必须从1开始");
        }
        if ((trainingPlanId == null) != (trainingPlanVersion == null)) {
            throw new IllegalArgumentException("trainingPlanId与trainingPlanVersion必须同时存在或同时为空");
        }
        if (trainingPlanVersion != null && trainingPlanVersion < 1) {
            throw new IllegalArgumentException("trainingPlanVersion必须从1开始");
        }
        if (snapshotContextJson == null || snapshotContextJson.isBlank()) {
            throw new IllegalArgumentException("snapshotContextJson不能为空");
        }

        snapshotHash = requireSha256(snapshotHash, "snapshotHash");
        artifactReferences = normalizeArtifacts(artifactReferences);
    }

    private static List<ArtifactReference> normalizeArtifacts(List<ArtifactReference> references) {
        if (references == null || references.isEmpty()) {
            throw new IllegalArgumentException("artifactReferences不能为空");
        }

        List<ArtifactReference> ordered = references.stream()
                .map(reference -> Objects.requireNonNull(reference, "artifactReference不能为空"))
                .sorted(Comparator.comparingInt(ArtifactReference::artifactOrder))
                .toList();

        Set<String> identities = new HashSet<>();
        for (int index = 0; index < ordered.size(); index++) {
            ArtifactReference reference = ordered.get(index);
            if (reference.artifactOrder() != index + 1) {
                throw new IllegalArgumentException("artifactOrder必须从1开始连续递增");
            }
            String identity = reference.artifactId() + ":" + reference.artifactVersion();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("同一证据版本不能重复加入输入快照");
            }
        }
        return ordered;
    }

    private static String requireSha256(String value, String fieldName) {
        if (value == null || !SHA256_PATTERN.matcher(value.strip()).matches()) {
            throw new IllegalArgumentException(fieldName + "必须是64位小写SHA-256");
        }
        return value.strip();
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存输入快照引用的不可变个人证据版本和顺序
     * @author: Miao Zheng
     * @date: 2026-08-27
     * @param artifactId 个人证据UUID
     * @param artifactVersion 个人证据业务版本
     * @param artifactSourceHash 证据版本的小写SHA-256
     * @param artifactOrder 快照内从1开始的稳定顺序
     **/
    public record ArtifactReference(
            UUID artifactId,
            long artifactVersion,
            String artifactSourceHash,
            int artifactOrder
    ) {

        public ArtifactReference {
            Objects.requireNonNull(artifactId, "artifactId不能为空");
            if (artifactVersion < 1) {
                throw new IllegalArgumentException("artifactVersion必须从1开始");
            }
            if (artifactOrder < 1) {
                throw new IllegalArgumentException("artifactOrder必须从1开始");
            }
            artifactSourceHash = requireSha256(artifactSourceHash, "artifactSourceHash");
        }
    }
}