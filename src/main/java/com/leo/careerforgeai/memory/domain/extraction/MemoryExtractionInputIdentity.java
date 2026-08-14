package com.leo.careerforgeai.memory.domain.extraction;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存一次Memory提取的规范来源、Extractor版本和稳定幂等Fingerprint
 * @author: Miao Zheng
 * @date: 2026-08-14
 * @param sessionId 来源Session
 * @param extractorVersion 提取Prompt和校验协议版本
 * @param inputFingerprint 规范输入的小写SHA-256
 * @param sources 按turnSequence排序的来源快照
 **/
public record MemoryExtractionInputIdentity(
        UUID sessionId,
        String extractorVersion,
        String inputFingerprint,
        List<MemoryExtractionSourceSnapshot> sources
) {

    public static final int MAX_SOURCE_TURNS = 20;

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public MemoryExtractionInputIdentity {
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        Objects.requireNonNull(sources, "sources不能为空");

        extractorVersion = normalizeVersion(extractorVersion);
        if (inputFingerprint == null
                || !SHA256_PATTERN.matcher(inputFingerprint).matches()) {
            throw new IllegalArgumentException("inputFingerprint必须是小写SHA-256");
        }
        if (sources.isEmpty() || sources.size() > MAX_SOURCE_TURNS
                || sources.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sources数量或内容不合法");
        }

        sources = List.copyOf(sources);
        Set<UUID> turnIds = new HashSet<>();
        long previousSequence = 0;

        for (MemoryExtractionSourceSnapshot source : sources) {
            if (!source.sessionId().equals(sessionId)) {
                throw new IllegalArgumentException("提取来源必须属于同一个Session");
            }
            if (!turnIds.add(source.turnId())) {
                throw new IllegalArgumentException("提取来源Turn不能重复");
            }
            if (source.turnSequence() <= previousSequence) {
                throw new IllegalArgumentException("提取来源必须按turnSequence严格递增");
            }
            previousSequence = source.turnSequence();
        }
    }

    private static String normalizeVersion(String value) {
        if (value == null) {
            throw new IllegalArgumentException("extractorVersion不能为空");
        }

        String normalized = value.strip();
        if (!VERSION_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("extractorVersion格式不合法");
        }
        return normalized;
    }
}