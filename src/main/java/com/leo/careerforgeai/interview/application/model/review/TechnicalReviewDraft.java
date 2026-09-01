package com.leo.careerforgeai.interview.application.model.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * @program: CareerForge-AI
 * @description: 定义技术评审角色生成但尚未持久化的结构化评价
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param dimensionScores 各允许评分维度的0至5分结果
 * @param coveredPoints 回答已经覆盖的关键点
 * @param errorsOrOmissions 技术错误、缺失或不充分之处
 * @param verificationBasis 评价依据和可验证理由
 * @param suggestedFollowUp 建议追问，空字符串表示无需追问
 **/
public record TechnicalReviewDraft(
        @NotNull @Size(min = 1, max = 10)
        Map<
                @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String,
                @NotNull @Min(0) @Max(5) Integer
                > dimensionScores,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> coveredPoints,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> errorsOrOmissions,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> verificationBasis,
        @NotNull @Size(max = 2_000) String suggestedFollowUp
) {
}