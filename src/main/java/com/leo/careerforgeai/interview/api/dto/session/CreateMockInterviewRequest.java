package com.leo.careerforgeai.interview.api.dto.session;

import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputSelection;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义创建模拟面试时选择的模式和不可变输入版本
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param requestId 客户端生成的幂等请求UUID
 * @param mode 模拟面试模式
 * @param targetRoleId 已确认目标岗位UUID
 * @param targetRoleVersion 目标岗位业务版本
 * @param skillGapSnapshotId 可选能力差距快照UUID
 * @param trainingPlanId 可选训练计划UUID
 * @param trainingPlanVersion 可选训练计划业务版本
 * @param artifactVersions 用户选择的个人证据版本
 **/
public record CreateMockInterviewRequest(
        @NotNull UUID requestId,
        @NotNull InterviewMode mode,
        @NotNull UUID targetRoleId,
        @Positive long targetRoleVersion,
        UUID skillGapSnapshotId,
        UUID trainingPlanId,
        @Positive Long trainingPlanVersion,
        @NotNull @Size(min = 1, max = 20) List<@Valid ArtifactVersionRequest> artifactVersions
) {

    public MockInterviewInputSelection toSelection() {
        return new MockInterviewInputSelection(
                targetRoleId,
                targetRoleVersion,
                skillGapSnapshotId,
                trainingPlanId,
                trainingPlanVersion,
                artifactVersions.stream().map(ArtifactVersionRequest::toSelection).toList()
        );
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义创建模拟面试时选择的一份个人证据版本
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param artifactId 个人证据UUID
     * @param artifactVersion 个人证据业务版本
     **/
    public record ArtifactVersionRequest(
            @NotNull UUID artifactId,
            @Positive long artifactVersion
    ) {

        public MockInterviewInputSelection.ArtifactVersion toSelection() {
            return new MockInterviewInputSelection.ArtifactVersion(artifactId, artifactVersion);
        }
    }
}