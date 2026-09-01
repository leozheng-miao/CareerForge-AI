package com.leo.careerforgeai.interview.application.model.review;

import com.leo.careerforgeai.interview.domain.review.EvidenceConsistencyVerdict;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 定义证据一致性角色生成但尚未持久化的结构化结论
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param verdict 回答与冻结证据的一致性结论
 * @param evidenceReferenceIds 支持该结论的证据片段ID
 * @param reason 仅描述材料支持程度的安全理由
 **/
public record EvidenceReviewDraft(
        @NotNull EvidenceConsistencyVerdict verdict,
        @NotNull @Size(max = 10) List<@Pattern(regexp = "[0-9a-f]{64}") String> evidenceReferenceIds,
        @NotBlank @Size(max = 2_000) String reason
) {
}