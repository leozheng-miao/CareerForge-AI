package com.leo.careerforgeai.interview.api.dto.answer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义当前问题答案的幂等提交和Graph异步恢复请求
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param roundNo 当前问题回合号
 * @param questionId 当前问题UUID
 * @param requestId 客户端生成的答案幂等请求UUID
 * @param expectedInterviewVersion 当前问题响应携带的面试版本
 * @param answerText 用户原始答案正文
 **/
public record SubmitInterviewAnswerRequest(
        @Positive int roundNo,
        @NotNull UUID questionId,
        @NotNull UUID requestId,
        @NotNull @PositiveOrZero Long expectedInterviewVersion,
        @NotBlank @Size(max = 12_000) String answerText
) {
}