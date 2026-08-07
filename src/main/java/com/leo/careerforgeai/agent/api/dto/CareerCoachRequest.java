package com.leo.careerforgeai.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @program: CareerForge-AI
 * @description: 定义Career Coach API唯一允许客户端提交的用户消息。
 * @author: Miao Zheng
 * @date: 2026-08-07 06:20
 **/
public record CareerCoachRequest(
        @NotBlank
        @Size(max = 12_000)
        String message
) {
}