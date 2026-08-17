package com.leo.careerforgeai.career.api.dto.targetrole;

import com.leo.careerforgeai.career.application.targetrole.TargetRoleDraftApplicationService;
import com.leo.careerforgeai.career.domain.TargetRoleDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @program: CareerForge-AI
 * @description: 定义创建目标岗位草案时允许客户端提交的JD来源和正文
 * @author: Miao Zheng
 * @date: 2026-08-16
 * @param sourceRef 原始JD的用户可见来源标识
 * @param jdText 需要解析的不可信JD原文
 */
public record CreateTargetRoleDraftRequest(
        @NotBlank
        @Size(max = TargetRoleDraft.MAX_SOURCE_REF_LENGTH)
        String sourceRef,

        @NotBlank
        @Size(max = TargetRoleDraftApplicationService.MAX_JD_LENGTH)
        String jdText
) {
}