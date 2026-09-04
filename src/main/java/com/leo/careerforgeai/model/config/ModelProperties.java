package com.leo.careerforgeai.model.config;

import lombok.Getter;

import java.net.URI;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存从统一模型路由配置派生的DeepSeek适配器连接参数，不直接绑定外部配置
 * @author: Miao Zheng
 * @date: 2026-07-28
 */
@Getter
public final class ModelProperties {
    private final URI baseUrl;
    private final String apiKey;
    private final String name;

    public ModelProperties(URI baseUrl, String apiKey, String name) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl不能为空");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("apiKey不能为空");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name不能为空");
        this.apiKey = apiKey.strip();
        this.name = name.strip();
    }
}