package com.leo.careerforgeai.interview.application.model.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义复盘教练角色生成待确认报告所需的已持久化事实摘要
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param interviewId 面试UUID
 * @param targetRoleSummary 已确认目标岗位摘要
 * @param roundReviewSummaries Java从已持久化回合和评审组装的摘要
 **/
public record InterviewReportInput(
        @NotNull UUID interviewId,
        @NotBlank @Size(max = 8_000) String targetRoleSummary,
        @NotNull @Size(min = 1, max = 20)
        List<@NotBlank @Size(max = 4_000) String> roundReviewSummaries
) {
}