package com.leo.careerforgeai.interview.application.model.review;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义证据一致性角色核对回答所需的冻结证据输入
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param interviewId 面试UUID
 * @param roundNo 回合号
 * @param questionId 已持久化问题UUID
 * @param answerId 已持久化答案UUID
 * @param question 问题正文
 * @param answer 用户原始回答
 * @param evidenceByChunkId 本轮允许引用的证据片段ID及内容
 **/
public record EvidenceReviewInput(
        @NotNull UUID interviewId,
        @Min(1) int roundNo,
        @NotNull UUID questionId,
        @NotNull UUID answerId,
        @NotBlank @Size(max = 2_000) String question,
        @NotBlank @Size(max = 12_000) String answer,
        @NotNull @Size(max = 20)
        Map<
                @Pattern(regexp = "[0-9a-f]{64}") String,
                @NotBlank @Size(max = 2_000) String
        > evidenceByChunkId
) {
}