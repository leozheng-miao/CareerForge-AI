package com.leo.careerforgeai.model.infrastructure.ollama.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-02 23:50
 **/
class OllamaEmbedDtoTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void shouldSerializeBatchRequestUsingOllamaFieldNames() throws Exception {
        OllamaEmbedRequest request = new OllamaEmbedRequest("qwen3-embedding:0.6b", List.of("文档一", "文档二"), false);

        String json = jsonMapper.writeValueAsString(request);

        assertThat(json).contains("\"model\":\"qwen3-embedding:0.6b\"");
        assertThat(json).contains("\"input\":[\"文档一\",\"文档二\"]");
        assertThat(json).contains("\"truncate\":false");
    }

    @Test
    void shouldDeserializeOllamaResponseAndIgnoreUnknownFields() throws Exception {
        String json = """
                {
                  "model": "qwen3-embedding:0.6b",
                  "embeddings": [[0.1, -0.2], [0.3, 0.4]],
                  "total_duration": 2105827375,
                  "load_duration": 1987379083,
                  "prompt_eval_count": 14,
                  "future_field": "ignored"
                }
                """;

        OllamaEmbedResponse response = jsonMapper.readValue(json, OllamaEmbedResponse.class);

        assertThat(response.model()).isEqualTo("qwen3-embedding:0.6b");
        assertThat(response.embeddings()).containsExactly(List.of(0.1F, -0.2F), List.of(0.3F, 0.4F));
        assertThat(response.totalDuration()).isEqualTo(2105827375L);
        assertThat(response.loadDuration()).isEqualTo(1987379083L);
        assertThat(response.promptEvalCount()).isEqualTo(14);
    }
}