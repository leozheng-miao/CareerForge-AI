package com.leo.careerforgeai.interview.application.snapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义创建模拟面试输入快照时由可信应用层提交的版本选择
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param targetRoleId 已确认目标岗位ID
 * @param targetRoleVersion 目标岗位业务版本
 * @param skillGapSnapshotId 可选能力差距快照ID
 * @param trainingPlanId 可选训练计划ID
 * @param trainingPlanVersion 可选训练计划业务版本
 * @param artifactVersions 用户选择的个人证据版本
 **/
public record MockInterviewInputSelection(
        UUID targetRoleId,
        long targetRoleVersion,
        UUID skillGapSnapshotId,
        UUID trainingPlanId,
        Long trainingPlanVersion,
        List<ArtifactVersion> artifactVersions
) {

    public MockInterviewInputSelection {
        Objects.requireNonNull(targetRoleId, "targetRoleId不能为空");
        if (targetRoleVersion < 1) throw new IllegalArgumentException("targetRoleVersion必须从1开始");
        if ((trainingPlanId == null) != (trainingPlanVersion == null)) {
            throw new IllegalArgumentException("trainingPlanId与trainingPlanVersion必须同时存在或同时为空");
        }
        if (trainingPlanVersion != null && trainingPlanVersion < 1) {
            throw new IllegalArgumentException("trainingPlanVersion必须从1开始");
        }
        if (trainingPlanId != null && skillGapSnapshotId == null) {
            throw new IllegalArgumentException("选择训练计划时必须同时选择能力差距快照");
        }
        if (artifactVersions == null || artifactVersions.isEmpty() || artifactVersions.size() > 20) {
            throw new IllegalArgumentException("artifactVersions数量必须在1到20之间");
        }

        artifactVersions = List.copyOf(artifactVersions);
        Set<String> identities = new HashSet<>();
        for (ArtifactVersion artifact : artifactVersions) {
            Objects.requireNonNull(artifact, "artifactVersion不能为空");
            String identity = artifact.artifactId() + ":" + artifact.artifactVersion();
            if (!identities.add(identity)) throw new IllegalArgumentException("个人证据版本不能重复选择");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 标识用户选择的一个不可变个人证据版本
     * @author: Miao Zheng
     * @date: 2026-08-27
     * @param artifactId 个人证据ID
     * @param artifactVersion 个人证据业务版本
     **/
    public record ArtifactVersion(UUID artifactId, long artifactVersion) {

        public ArtifactVersion {
            Objects.requireNonNull(artifactId, "artifactId不能为空");
            if (artifactVersion < 1) throw new IllegalArgumentException("artifactVersion必须从1开始");
        }
    }
}