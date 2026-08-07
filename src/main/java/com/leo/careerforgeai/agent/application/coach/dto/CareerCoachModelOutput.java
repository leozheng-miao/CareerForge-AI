package com.leo.careerforgeai.agent.application.coach.dto;

import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 定义Career Coach模型最终结构化输出的校验边界。
 * @author: Miao Zheng
 * @date: 2026-08-07 02:40
 **/
public record CareerCoachModelOutput(
        @NotNull
        CareerCoachAnswerStatus status,

        @NotBlank
        @Size(max = 8_000)
        String answer,

        @NotNull
        @Size(max = 10)
        List<
                @NotBlank
                @Pattern(regexp = "[0-9a-f]{64}")
                String
        > citedChunkIds
) {
}