package com.leo.careerforgeai.memory.api.dto;

import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 返回当前用户确定版本的已确认技能证据画像
 * @author: Miao Zheng
 * @date: 2026-08-17
 * @param profileVersion 技能画像有效状态变更版本
 * @param skillEvidence 当前版本包含的已确认技能证据
 */
public record ConfirmedSkillProfileResponse(
        long profileVersion,
        List<MemoryCandidateResponse> skillEvidence
) {
    public static ConfirmedSkillProfileResponse from(ConfirmedSkillProfile profile) {
        return new ConfirmedSkillProfileResponse(
                profile.profileVersion(),
                profile.skillEvidence().stream()
                        .map(MemoryCandidateResponse::from)
                        .toList()
        );
    }
}