package com.leo.careerforgeai.interview.api.dto.session;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @program: CareerForge-AI
 * @description: 定义异步启动模拟面试时要求匹配的面试版本
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param expectedVersion 客户端最后读取到的面试乐观锁版本
 **/
public record StartMockInterviewRequest(
        @NotNull @PositiveOrZero Long expectedVersion
) {
}