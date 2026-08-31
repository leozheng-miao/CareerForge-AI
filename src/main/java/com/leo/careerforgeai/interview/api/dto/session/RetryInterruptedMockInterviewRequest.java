package com.leo.careerforgeai.interview.api.dto.session;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @program: CareerForge-AI
 * @description: 定义重新执行中断报告节点时要求匹配的面试版本
 * @author: Miao Zheng
 * @date: 2026-08-31
 * @param expectedVersion 客户端最后读取到的面试乐观锁版本
 */
public record RetryInterruptedMockInterviewRequest(
        @NotNull @PositiveOrZero Long expectedVersion
) {
}