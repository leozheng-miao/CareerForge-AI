package com.leo.careerforgeai.knowledge.application.answer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 接收模型生成的回答正文和候选上下文 Chunk ID
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public record RagAnswerModelOutput(
        @NotBlank
        @Size(max = 8_000)
        String answer,

        @NotNull
        @Size(max = 20)
        List<@NotBlank @Pattern(regexp = "[0-9a-f]{64}") String> citedChunkIds
) {
}