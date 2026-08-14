package com.leo.careerforgeai.memory.application.extraction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 定义Memory提取模型返回的顶层结构化输出，仅包含待校验的候选列表
 * @author: Miao Zheng
 * @date: 2026-08-13
 * @param candidates 模型提出的Memory候选，允许为空但单次不能超过服务端上限
 **/
public record MemoryExtractionModelOutput(
        @NotNull
        @Size(max = 10)
        List<@Valid @NotNull MemoryCandidateModelOutput> candidates) {
}