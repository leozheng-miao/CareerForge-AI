package com.leo.careerforgeai.career.api.dto;

import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.TargetRole;

import java.time.Instant;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回当前用户已经确认且不可原地修改的目标岗位版本
 * @author: Miao Zheng
 * @date: 2026-08-16
 * @param targetRoleId 目标岗位版本ID
 * @param targetRoleVersion 当前用户的目标岗位业务版本
 * @param sourceRef 原始JD来源标识
 * @param sourceHash 原始JD正文Hash
 * @param parserVersion 解析器版本
 * @param promptVersion Prompt版本
 * @param requirements 冻结的岗位要求快照
 * @param confirmedAt 用户确认时间
 */
public record TargetRoleResponse(
        UUID targetRoleId,
        long targetRoleVersion,
        String sourceRef,
        String sourceHash,
        String parserVersion,
        String promptVersion,
        JobRequirements requirements,
        Instant confirmedAt
) {

    public static TargetRoleResponse from(
            TargetRole targetRole
    ) {
        return new TargetRoleResponse(
                targetRole.targetRoleId(),
                targetRole.targetRoleVersion(),
                targetRole.sourceRef(),
                targetRole.sourceHash(),
                targetRole.parserVersion(),
                targetRole.promptVersion(),
                targetRole.requirementsSnapshot(),
                targetRole.confirmedAt()
        );
    }
}