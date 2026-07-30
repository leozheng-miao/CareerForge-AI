package com.leo.careerforgeai.model.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * @program: CareerForge-AI
 * @description: 配置模型供应商调用使用的 Java HTTP 客户端。
 * @author: Miao Zheng
 * @date: 2026-07-30 17:29
 **/
@Configuration
public class ModelHttpClientConfig {

    @Bean
    public HttpClient modelHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

}