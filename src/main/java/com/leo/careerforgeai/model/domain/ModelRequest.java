package com.leo.careerforgeai.model.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 定义模型请求的消息、输出格式、输出预算、温度和单次调用Deadline
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param messages 模型消息
 * @param outputFormat 输出格式
 * @param maxOutputTokens 最大输出Token数
 * @param temperature 生成温度
 * @param timeout 当前调用允许使用的最长时间
 **/
public record ModelRequest(
        List<ModelMessage> messages,
        ModelOutputFormat outputFormat,
        int maxOutputTokens,
        double temperature,
        Duration timeout
) {
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 2_000;
    private static final double DEFAULT_TEMPERATURE = 1.0;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public ModelRequest {
        Objects.requireNonNull(messages, "messages不能为空");
        Objects.requireNonNull(outputFormat, "outputFormat不能为空");
        Objects.requireNonNull(timeout, "timeout不能为空");
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens必须大于0");
        if (!Double.isFinite(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature必须在0到2之间");
        }
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout必须大于0");
        messages = List.copyOf(messages);
    }

    public ModelRequest(List<ModelMessage> messages, ModelOutputFormat outputFormat) {
        this(messages, outputFormat, DEFAULT_MAX_OUTPUT_TOKENS, DEFAULT_TEMPERATURE, DEFAULT_TIMEOUT);
    }

    public ModelRequest(
            List<ModelMessage> messages,
            ModelOutputFormat outputFormat,
            Duration timeout
    ) {
        this(messages, outputFormat, DEFAULT_MAX_OUTPUT_TOKENS, DEFAULT_TEMPERATURE, timeout);
    }

    public ModelRequest withTimeout(Duration timeout) {
        return new ModelRequest(messages, outputFormat, maxOutputTokens, temperature, timeout);
    }

    public ModelRequest withMaxOutputTokens(int maxOutputTokens) {
        return new ModelRequest(messages, outputFormat, maxOutputTokens, temperature, timeout);
    }

    public ModelRequest withTemperature(double temperature) {
        return new ModelRequest(messages, outputFormat, maxOutputTokens, temperature, timeout);
    }
}