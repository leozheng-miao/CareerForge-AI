package com.leo.careerforgeai.interview.application.model.contract;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义技术评审角色评价单轮回答所需的冻结输入
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param interviewId 面试UUID
 * @param roundNo 回合号
 * @param questionId 已持久化问题UUID
 * @param answerId 已持久化答案UUID
 * @param question 问题正文
 * @param answer 用户原始回答
 * @param targetRoleRequirements 本轮相关岗位要求
 * @param scoreDimensions Java允许的评分维度
 * @param scoringRubric 各评分维度的确定性评分规则
 **/
public record TechnicalReviewInput(
        @NotNull UUID interviewId,
        @Min(1) int roundNo,
        @NotNull UUID questionId,
        @NotNull UUID answerId,
        @NotBlank @Size(max = 2_000) String question,
        @NotBlank @Size(max = 12_000) String answer,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1_000) String> targetRoleRequirements,
        @NotNull @Size(min = 1, max = 10) List<@Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String> scoreDimensions,
        @NotNull @Size(min = 1, max = 20) List<@NotBlank @Size(max = 1_000) String> scoringRubric
) {
}