package com.leo.careerforgeai.agent.domain.tool.career;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @program: CareerForge-AI
 * @description: 定义岗位解析工具接收的有界JD文本。
 * @author: Miao Zheng
 * @date: 2026-08-07 01:10
 **/
public record ParseJobRequirementsInput(
        @NotBlank
        @Size(max = 12_000)
        String jdText
) {
}