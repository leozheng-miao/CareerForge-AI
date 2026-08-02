package com.leo.careerforgeai.model.infrastructure.ollama;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-31 14:42
 **/
@ConfigurationProperties(prefix = "careerforge.embedding.ollama", ignoreUnknownFields = false)
@Validated
@Getter
public class OllamaEmbeddingProperties {
    @NotNull
    private final URI baseUrl;

    @NotBlank
    private final String model;

    @Positive
    private final int dimensions;

    public OllamaEmbeddingProperties(URI baseUrl, String model, int dimensions) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimensions = dimensions;
    }
}