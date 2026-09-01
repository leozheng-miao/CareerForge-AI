package com.leo.careerforgeai.interview.api.dto.report;

import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 接收报告乐观锁版本、请求幂等键和全部建议的逐项用户决定
 * @author: Miao Zheng
 * @date: 2026-08-31
 * @param requestId 客户端生成的本次确认幂等UUID
 * @param expectedVersion 用户读取报告时看到的乐观锁version
 * @param decisions 报告全部建议的确认或拒绝决定
 */
public record ConfirmInterviewReportRequest(
        @NotNull UUID requestId,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull @Size(max = 20) @Valid List<DecisionRequest> decisions
) {

    /**
     * @program: CareerForge-AI
     * @description: 表示用户对一条报告建议的决定
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param suggestionId 报告建议UUID
     * @param decisionType CONFIRMED或REJECTED
     */
    public record DecisionRequest(
            @NotNull UUID suggestionId,
            @NotNull InterviewReportConfirmation.DecisionType decisionType
    ) {
    }
}