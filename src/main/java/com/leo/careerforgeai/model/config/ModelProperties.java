package com.leo.careerforgeai.model.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-28 16:21
 **/
@ConfigurationProperties(prefix = "careerforge.model", ignoreUnknownFields = false)
@Validated
@Getter
public final class ModelProperties {

    @NotNull
    private final URI baseUrl;
    @NotBlank
    private final String apiKey;
    @NotBlank
    private final String name;

    public ModelProperties(URI baseUrl, String apiKey, String name) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.name = name;
    }
}