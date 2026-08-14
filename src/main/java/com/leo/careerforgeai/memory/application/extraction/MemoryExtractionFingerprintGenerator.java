package com.leo.careerforgeai.memory.application.extraction;

import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionInputIdentity;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionSourceSnapshot;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 根据Extractor版本和规范化Turn来源生成稳定的Memory提取幂等身份
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@Component
public final class MemoryExtractionFingerprintGenerator {

    public static final String CURRENT_EXTRACTOR_VERSION =
            "memory-candidate-extractor-v1";

    public MemoryExtractionInputIdentity generate(List<ConversationTurn> turns) {
        return generate(turns, CURRENT_EXTRACTOR_VERSION);
    }

    MemoryExtractionInputIdentity generate(
            List<ConversationTurn> turns,
            String extractorVersion
    ) {
        if (turns == null || turns.isEmpty()
                || turns.size() > MemoryExtractionInputIdentity.MAX_SOURCE_TURNS
                || turns.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("提取来源Turn数量或内容不合法");
        }

        List<MemoryExtractionSourceSnapshot> sources = turns.stream()
                .map(MemoryExtractionSourceSnapshot::from)
                .sorted(Comparator.comparingLong(
                        MemoryExtractionSourceSnapshot::turnSequence
                ))
                .toList();

        String normalizedVersion = normalizeVersion(extractorVersion);
        StringBuilder canonicalInput = new StringBuilder();
        appendLengthPrefixed(canonicalInput, normalizedVersion);
        appendLengthPrefixed(
                canonicalInput,
                sources.getFirst().sessionId().toString()
        );

        for (MemoryExtractionSourceSnapshot source : sources) {
            appendLengthPrefixed(canonicalInput, source.turnId().toString());
            appendLengthPrefixed(
                    canonicalInput,
                    Long.toString(source.turnSequence())
            );
            appendLengthPrefixed(canonicalInput, source.sourceHash());
        }

        return new MemoryExtractionInputIdentity(
                sources.getFirst().sessionId(),
                normalizedVersion,
                sha256(canonicalInput.toString()),
                sources
        );
    }

    private static String normalizeVersion(String extractorVersion) {
        if (extractorVersion == null) {
            throw new IllegalArgumentException("extractorVersion不能为空");
        }

        String normalized = extractorVersion.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("extractorVersion不能为空");
        }
        return normalized;
    }

    private static void appendLengthPrefixed(
            StringBuilder builder,
            String value
    ) {
        builder.append(value.length())
                .append(':')
                .append(value);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JDK不支持SHA-256",
                    exception
            );
        }
    }
}