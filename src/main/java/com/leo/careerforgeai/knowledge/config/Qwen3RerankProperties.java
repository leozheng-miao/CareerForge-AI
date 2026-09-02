package com.leo.careerforgeai.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * @program: CareerForge-AI
 * @description: 配置Qwen3文本重排生产Adapter的端点、凭证和输入边界。
 * @author: Miao Zheng
 * @date: 2026-09-02
 * @param endpoint 完整reranks端点
 * @param apiKey 阿里云百炼API Key
 * @param model 固定模型名称
 * @param maxCandidates 项目允许的最大候选数量
 * @param maxDocumentChars 单个候选允许的最大字符数
 * @param timeout 单次调用Deadline
 * @param instruct 固定英文排序指令
 */
@Validated
@ConfigurationProperties(
        prefix = "careerforge.knowledge.rerank.qwen3",
        ignoreUnknownFields = false
)
public record Qwen3RerankProperties(
        String endpoint,
        String apiKey,
        String model,
        int maxCandidates,
        int maxDocumentChars,
        Duration timeout,
        String instruct
) {

    public Qwen3RerankProperties {
        endpoint = endpoint == null ? "" : endpoint.strip();
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = requireText(model, "model", 64);
        instruct = requireText(instruct, "instruct", 500);
        if (!"qwen3-rerank".equals(model)) {
            throw new IllegalArgumentException("当前Adapter只允许qwen3-rerank");
        }
        if (maxCandidates < 1 || maxCandidates > 100) {
            throw new IllegalArgumentException("maxCandidates必须在1到100之间");
        }
        if (maxDocumentChars < 1 || maxDocumentChars > 20_000) {
            throw new IllegalArgumentException(
                    "maxDocumentChars必须在1到20000之间");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException(
                    "timeout必须大于0且不超过2分钟");
        }
    }

    public URI requiredEndpoint() {
        if (endpoint.isBlank()) {
            throw new IllegalArgumentException(
                    "启用Qwen3 Rerank时endpoint不能为空");
        }
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Qwen3 Rerank endpoint格式非法", exception);
        }
        boolean loopback = "127.0.0.1".equals(uri.getHost())
                || "localhost".equalsIgnoreCase(uri.getHost());
        if (uri.getHost() == null
                || (!"https".equalsIgnoreCase(uri.getScheme())
                && !(loopback && "http".equalsIgnoreCase(uri.getScheme())))
                || !uri.getPath().endsWith("/reranks")) {
            throw new IllegalArgumentException(
                    "Qwen3 Rerank endpoint必须是HTTPS reranks端点");
        }
        return uri;
    }

    public String requiredApiKey() {
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "启用Qwen3 Rerank时apiKey不能为空");
        }
        return apiKey;
    }

    private static String requireText(
            String value, String field, int maxLength) {
        if (value == null || value.isBlank()
                || value.strip().length() > maxLength) {
            throw new IllegalArgumentException(
                    field + "不能为空且长度不能超过" + maxLength);
        }
        return value.strip();
    }
}