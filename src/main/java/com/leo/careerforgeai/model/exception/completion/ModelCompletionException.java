package com.leo.careerforgeai.model.exception.completion;

import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示供应商返回了响应但模型输出未正常完整结束，仅保留安全诊断信息
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
public final class ModelCompletionException extends ModelException {

    private static final int MAX_DIAGNOSTIC_VALUE_LENGTH = 128;

    private final ModelCompletionStatus completionStatus;
    private final String providerFinishReason;
    private final String providerRequestId;
    private final String model;
    private final ModelUsage usage;
    private final long durationMs;
    private final int outputChars;
    private final String outputSha256;

    public ModelCompletionException(
            ModelCompletionStatus completionStatus,
            String providerFinishReason,
            String providerRequestId,
            String model,
            ModelUsage usage,
            long durationMs,
            String partialOutput
    ) {
        super(resolveErrorType(completionStatus), "模型供应商输出未完整结束");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs不能小于0");
        String safeOutput = partialOutput == null ? "" : partialOutput;
        this.completionStatus = completionStatus;
        this.providerFinishReason = safeValue(providerFinishReason);
        this.providerRequestId = safeValue(providerRequestId);
        this.model = safeValue(model);
        this.usage = usage;
        this.durationMs = durationMs;
        this.outputChars = safeOutput.length();
        this.outputSha256 = sha256(safeOutput);
    }

    public ModelCompletionStatus completionStatus() {
        return completionStatus;
    }

    public String providerFinishReason() {
        return providerFinishReason;
    }

    public String providerRequestId() {
        return providerRequestId;
    }

    public String model() {
        return model;
    }

    public ModelUsage usage() {
        return usage;
    }

    public long durationMs() {
        return durationMs;
    }

    public int outputChars() {
        return outputChars;
    }

    public String outputSha256() {
        return outputSha256;
    }

    private static ModelErrorType resolveErrorType(ModelCompletionStatus status) {
        Objects.requireNonNull(status, "completionStatus不能为空");
        if (status == ModelCompletionStatus.COMPLETED) {
            throw new IllegalArgumentException("COMPLETED不能构造完成异常");
        }
        return status == ModelCompletionStatus.PROVIDER_RESOURCE_INTERRUPTED
                ? ModelErrorType.PROVIDER_UNAVAILABLE
                : ModelErrorType.PROVIDER_INCOMPLETE;
    }

    private static String safeValue(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        String normalized = value
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_');
        return normalized.length() <= MAX_DIAGNOSTIC_VALUE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_DIAGNOSTIC_VALUE_LENGTH);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }
}