package com.leo.careerforgeai.interviewflow.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

@Validated
@ConfigurationProperties(
        prefix = "careerforge.interview-flow.resume-parsing",
        ignoreUnknownFields = false
)
public record ResumeParsingProperties(
        @NotNull Path tempDirectory,
        @NotNull DataSize maxUploadSize,
        @Min(2) @Max(2_000) int maxDocxEntries,
        @NotNull DataSize maxDocxExpandedSize,
        @Min(1) @Max(1_000_000) int maxTextCharacters,
        @NotNull Duration timeout
) {
    public ResumeParsingProperties {
        Objects.requireNonNull(tempDirectory, "tempDirectory不能为null");
        requirePositive(maxUploadSize, "maxUploadSize");
        requirePositive(maxDocxExpandedSize, "maxDocxExpandedSize");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout必须大于0");
        }
        if (maxDocxEntries < 2 || maxDocxEntries > 2_000) {
            throw new IllegalArgumentException("maxDocxEntries必须在2到2000之间");
        }
        if (maxTextCharacters < 1 || maxTextCharacters > 1_000_000) {
            throw new IllegalArgumentException("maxTextCharacters不合法");
        }
    }

    private static void requirePositive(DataSize value, String field) {
        if (value == null || value.toBytes() <= 0) {
            throw new IllegalArgumentException(field + "必须大于0");
        }
    }
}
