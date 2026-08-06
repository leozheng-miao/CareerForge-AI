package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * @program: CareerForge-AI
 * @description: 对工具名、规范化参数和服务端上下文版本生成不可逆重复调用指纹。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:25
 */
public final class ToolCallFingerprintService {

    private final JsonMapper jsonMapper;

    public ToolCallFingerprintService(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper 不能为空");
    }

    public String fingerprint(ToolCall toolCall, String contextVersion) {
        Objects.requireNonNull(toolCall, "toolCall 不能为空");
        if (contextVersion == null || contextVersion.isBlank()) {
            throw new IllegalArgumentException("contextVersion 不能为空");
        }

        String canonicalArguments = canonicalize(toolCall.argumentsJson());
        String source = lengthPrefixed(toolCall.name())
                + lengthPrefixed(contextVersion)
                + lengthPrefixed(canonicalArguments);

        return sha256(source);
    }

    private String canonicalize(String argumentsJson) {
        try {
            Object parsed = jsonMapper.readValue(argumentsJson, Object.class);
            return jsonMapper.writeValueAsString(normalize(parsed));
        } catch (JacksonException exception) {
            return "INVALID:" + argumentsJson.strip();
        }
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return sorted;
        }

        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) normalized.add(normalize(item));
            return normalized;
        }

        return value;
    }

    private String lengthPrefixed(String value) {
        return value.length() + ":" + value;
    }

    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}