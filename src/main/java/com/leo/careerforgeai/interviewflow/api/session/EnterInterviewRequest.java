package com.leo.careerforgeai.interviewflow.api.session;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 接收正式进入面试的请求身份和期望版本
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record EnterInterviewRequest(
        @NotNull UUID requestId,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
