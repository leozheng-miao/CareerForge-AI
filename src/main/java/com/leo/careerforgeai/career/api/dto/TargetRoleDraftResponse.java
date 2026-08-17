package com.leo.careerforgeai.career.api.dto;

import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.TargetRoleDraft;

import java.time.Instant;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回当前用户可审阅的目标岗位草案及其来源和解析版本
 * @author: Miao Zheng
 * @date: 2026-08-16
 * @param draftId 服务端生成的草案ID
 * @param sourceRef 原始JD来源标识
 * @param sourceHash 原始JD正文Hash
 * @param parserVersion 解析器版本
 * @param promptVersion Prompt版本
 * @param requirements 等待用户确认的结构化岗位要求
 * @param status 草案状态
 * @param version 后续确认操作使用的版本
 * @param createdAt 草案创建时间
 */
public record TargetRoleDraftResponse(
        UUID draftId,
        String sourceRef,
        String sourceHash,
        String parserVersion,
        String promptVersion,
        JobRequirements requirements,
        TargetRoleDraft.Status status,
        long version,
        Instant createdAt,
        UUID confirmedTargetRoleId,
        Long confirmedTargetRoleVersion,
        Instant confirmedAt
) {

    public static TargetRoleDraftResponse from(
            TargetRoleDraft draft
    ) {
        return new TargetRoleDraftResponse(
                draft.draftId(),
                draft.sourceRef(),
                draft.sourceHash(),
                draft.parserVersion(),
                draft.promptVersion(),
                draft.requirementsSnapshot(),
                draft.status(),
                draft.version(),
                draft.createdAt(),
                draft.confirmedTargetRoleId(),
                draft.confirmedTargetRoleVersion(),
                draft.confirmedAt()
        );
    }
}