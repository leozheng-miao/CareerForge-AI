package com.leo.careerforgeai.model.infrastructure.ollama.dto;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 表示发送给 Ollama /api/embed 的批量 Embedding JSON 请求
 * @author: Miao Zheng
 * @date: 2026-08-02 23:47
 **/
public record OllamaEmbedRequest(
        String model,
        List<String> input,
        boolean truncate
) {
}