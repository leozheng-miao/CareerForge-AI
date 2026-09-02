package com.leo.careerforgeai.model.infrastructure.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 定义DeepSeek Chat Completions请求协议。
 * @author: Miao Zheng
 * @date: 2026-09-02
 * @param responseFormat 响应格式
 * @param model 模型名称
 * @param messages 消息集合
 * @param maxTokens 最大输出Token数
 * @param temperature 非Thinking模式生成温度
 * @param thinking Thinking开关
 * @param reasoningEffort Thinking推理强度
 * @param stream 是否流式调用
 * @param streamOptions 流式选项
 */
public record DeepSeekChatRequest(
        @JsonProperty("response_format") ResponseFormat responseFormat,
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") int maxTokens,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double temperature,
        Thinking thinking,
        @JsonProperty("reasoning_effort")
        @JsonInclude(JsonInclude.Include.NON_NULL) String reasoningEffort,
        boolean stream,
        @JsonProperty("stream_options")
        @JsonInclude(JsonInclude.Include.NON_NULL) StreamOptions streamOptions
) {

    public record Message(String role, String content) {}

    public record Thinking(String type) {}

    public record ResponseFormat(String type) {}

    public record StreamOptions(@JsonProperty("include_usage") boolean includeUsage) {}
}