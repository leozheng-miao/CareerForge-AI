package com.leo.careerforgeai.model.infrastructure.ollama.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 接收 Ollama 返回的模型名称、批量向量和调用统计字段
 * @author: Miao Zheng
 * @date: 2026-08-02 23:49
 **/
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaEmbedResponse(
        String model,
        List<List<Float>> embeddings,
        @JsonProperty("total_duration") Long totalDuration,
        @JsonProperty("load_duration") Long loadDuration,
        @JsonProperty("prompt_eval_count") Integer promptEvalCount
) {
}