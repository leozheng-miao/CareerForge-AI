package com.leo.careerforgeai.agent.application.coach.validation;

import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.model.exception.structured.StructuredOutputException;
import com.leo.careerforgeai.model.exception.structured.StructuredOutputFailureReason;
import com.leo.careerforgeai.model.exception.structured.StructuredOutputFailureStage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示Career Coach最终回答无法通过Java可信边界校验。
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
public final class CareerCoachFinalAnswerException extends RuntimeException {

    private final CareerCoachFinalAnswerErrorType errorType;
    private final AgentRunTrace trace;
    private final StructuredOutputException structuredFailure;
    private final Integer outputChars;
    private final String outputSha256;

    public CareerCoachFinalAnswerException(CareerCoachFinalAnswerErrorType errorType, String safeMessage) {
        this(errorType, safeMessage, null, null, null, null, null);
    }

    public CareerCoachFinalAnswerException(CareerCoachFinalAnswerErrorType errorType, String safeMessage, Throwable cause) {
        this(errorType, safeMessage, cause, null, null, null, null);
    }

    public CareerCoachFinalAnswerException(CareerCoachFinalAnswerErrorType errorType, String safeMessage,
                                           StructuredOutputException structuredFailure, String rawOutput) {
        this(errorType, safeMessage, structuredFailure, null, structuredFailure,
                rawOutput == null ? null : rawOutput.length(), rawOutput == null ? null : sha256(rawOutput));
    }

    private CareerCoachFinalAnswerException(CareerCoachFinalAnswerErrorType errorType, String safeMessage,
                                            Throwable cause, AgentRunTrace trace,
                                            StructuredOutputException structuredFailure,
                                            Integer outputChars, String outputSha256) {
        super(safeMessage, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
        this.trace = trace;
        this.structuredFailure = structuredFailure;
        this.outputChars = outputChars;
        this.outputSha256 = outputSha256;
    }

    public CareerCoachFinalAnswerException withTrace(AgentRunTrace trace) {
        return new CareerCoachFinalAnswerException(errorType, getMessage(), this,
                Objects.requireNonNull(trace, "trace不能为空"), structuredFailure, outputChars, outputSha256);
    }

    public CareerCoachFinalAnswerErrorType getErrorType() {
        return errorType;
    }

    public AgentRunTrace getTrace() {
        return trace;
    }

    public StructuredOutputFailureStage getFailureStage() {
        return structuredFailure == null ? null : structuredFailure.failureStage();
    }

    public StructuredOutputFailureReason getFailureReason() {
        return structuredFailure == null ? null : structuredFailure.failureReason();
    }

    public String getFieldPath() {
        return structuredFailure == null ? null : structuredFailure.fieldPath();
    }

    public Integer getOutputChars() {
        return outputChars;
    }

    public String getOutputSha256() {
        return outputSha256;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }
}