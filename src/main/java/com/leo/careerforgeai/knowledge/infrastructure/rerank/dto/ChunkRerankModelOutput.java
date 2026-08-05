package com.leo.careerforgeai.knowledge.infrastructure.rerank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 接收 LLM 返回的完整候选 Chunk ID 排序
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public record ChunkRerankModelOutput(
        @NotNull
        @Size(min = 1, max = 20)
        List<@NotBlank @Pattern(regexp = "[0-9a-f]{64}") String> chunkIds
) {
}