package com.leo.careerforgeai.model.infrastructure.json;

import com.leo.careerforgeai.model.exception.structured.StructuredOutputException;
import com.leo.careerforgeai.model.exception.structured.StructuredOutputFailureReason;
import com.leo.careerforgeai.model.exception.structured.StructuredOutputFailureStage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 将Jackson 3解析和反序列化异常转换为稳定且不泄露正文的结构化失败分类
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
public final class JacksonStructuredOutputFailureClassifier {

    private static final Pattern SAFE_SEGMENT =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,63}");

    private JacksonStructuredOutputFailureClassifier() {
    }

    public static StructuredOutputException parsing(
            JacksonException exception,
            String safeMessage
    ) {
        Objects.requireNonNull(exception, "exception不能为空");
        return new StructuredOutputException(
                StructuredOutputFailureStage.JSON_PARSING,
                StructuredOutputFailureReason.MALFORMED_JSON,
                null,
                safeMessage,
                exception
        );
    }

    public static StructuredOutputException deserialization(
            JacksonException exception,
            String safeMessage
    ) {
        Objects.requireNonNull(exception, "exception不能为空");
        StructuredOutputFailureReason reason;

        if (exception instanceof UnrecognizedPropertyException) {
            reason = StructuredOutputFailureReason.UNKNOWN_FIELD;
        } else if (exception instanceof InvalidFormatException invalid
                && invalid.getTargetType() != null
                && invalid.getTargetType().isEnum()) {
            reason = StructuredOutputFailureReason.INVALID_ENUM_VALUE;
        } else if (exception instanceof MismatchedInputException) {
            reason = StructuredOutputFailureReason.FIELD_TYPE_MISMATCH;
        } else {
            reason = StructuredOutputFailureReason.DTO_DESERIALIZATION_FAILED;
        }

        return new StructuredOutputException(
                StructuredOutputFailureStage.DTO_DESERIALIZATION,
                reason,
                fieldPath(exception),
                safeMessage,
                exception
        );
    }

    private static String fieldPath(JacksonException exception) {
        StringBuilder path = new StringBuilder("$");
        exception.getPath().forEach(reference -> {
            if (reference.getPropertyName() != null) {
                path.append('.').append(safeSegment(reference.getPropertyName()));
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        });

        if (exception instanceof UnrecognizedPropertyException unrecognized) {
            String segment = safeSegment(unrecognized.getPropertyName());
            if (!path.toString().endsWith("." + segment)) path.append('.').append(segment);
        }
        return path.length() == 1 ? null : path.toString();
    }

    private static String safeSegment(String value) {
        return value != null && SAFE_SEGMENT.matcher(value).matches()
                ? value : "<redacted>";
    }
}