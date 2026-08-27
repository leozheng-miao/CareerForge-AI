package com.leo.careerforgeai.interview.application.evidence;

import com.leo.careerforgeai.interview.domain.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceStatus;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceType;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 将文本或Markdown规范化并生成带稳定Hash和Unicode码点片段的个人证据版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public class PersonalEvidenceArtifactFactory {

    private static final String CHUNKER_VERSION = "personal-evidence-code-point-v1";
    private static final int MAX_CHUNK_CODE_POINTS = 2000;
    private static final int MIN_BOUNDARY_CODE_POINTS = 1000;

    public PersonalEvidenceArtifact create(
            UUID artifactId,
            long artifactVersion,
            ActorId ownerId,
            PersonalEvidenceType type,
            String sourceName,
            String rawContent,
            Instant createdAt
    ) {
        Objects.requireNonNull(artifactId, "artifactId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(type, "type不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");

        String content = normalize(rawContent);
        String sourceHash = sha256(content);
        List<PersonalEvidenceArtifact.Chunk> chunks = createChunks(
                artifactId, artifactVersion, sourceHash, content, createdAt
        );

        return new PersonalEvidenceArtifact(
                artifactId,
                artifactVersion,
                ownerId,
                type,
                sourceName,
                sourceHash,
                content,
                PersonalEvidenceStatus.ACTIVE,
                null,
                chunks,
                createdAt,
                createdAt,
                null,
                null
        );
    }

    private String normalize(String rawContent) {
        if (rawContent == null) throw new IllegalArgumentException("rawContent不能为空");

        String content = rawContent.startsWith("\uFEFF") ? rawContent.substring(1) : rawContent;
        content = content.replace("\r\n", "\n").replace('\r', '\n').strip();

        if (content.isBlank()) throw new IllegalArgumentException("rawContent规范化后不能为空");
        if (content.codePointCount(0, content.length()) > 100000) {
            throw new IllegalArgumentException("rawContent长度不能超过100000");
        }
        return content;
    }

    private List<PersonalEvidenceArtifact.Chunk> createChunks(
            UUID artifactId,
            long artifactVersion,
            String sourceHash,
            String content,
            Instant createdAt
    ) {
        int[] codePoints = content.codePoints().toArray();
        List<PersonalEvidenceArtifact.Chunk> chunks = new ArrayList<>();
        int startOffset = 0;

        while (startOffset < codePoints.length) {
            while (startOffset < codePoints.length && Character.isWhitespace(codePoints[startOffset])) startOffset++;
            if (startOffset == codePoints.length) break;

            int maximumEnd = Math.min(startOffset + MAX_CHUNK_CODE_POINTS, codePoints.length);
            int endOffset = maximumEnd == codePoints.length
                    ? maximumEnd
                    : findPreferredBoundary(codePoints, startOffset, maximumEnd);

            while (endOffset > startOffset && Character.isWhitespace(codePoints[endOffset - 1])) endOffset--;
            if (endOffset == startOffset) endOffset = maximumEnd;

            String chunkContent = new String(codePoints, startOffset, endOffset - startOffset);
            String contentHash = sha256(chunkContent);
            int chunkIndex = chunks.size() + 1;
            String chunkId = chunkId(
                    artifactId,
                    artifactVersion,
                    sourceHash,
                    chunkIndex,
                    startOffset,
                    endOffset,
                    contentHash
            );

            chunks.add(new PersonalEvidenceArtifact.Chunk(
                    chunkId,
                    chunkIndex,
                    startOffset,
                    endOffset,
                    chunkContent,
                    contentHash,
                    createdAt
            ));
            startOffset = endOffset;
        }

        if (chunks.isEmpty()) throw new IllegalArgumentException("正文没有可生成的证据片段");
        return List.copyOf(chunks);
    }

    private int findPreferredBoundary(int[] codePoints, int startOffset, int maximumEnd) {
        int minimumEnd = Math.min(startOffset + MIN_BOUNDARY_CODE_POINTS, maximumEnd);

        for (int index = maximumEnd - 1; index > minimumEnd; index--) {
            if (codePoints[index] == '\n' && codePoints[index - 1] == '\n') return index - 1;
        }
        for (int index = maximumEnd - 1; index >= minimumEnd; index--) {
            if (codePoints[index] == '\n') return index;
        }
        for (int index = maximumEnd - 1; index >= minimumEnd; index--) {
            if (Character.isWhitespace(codePoints[index])) return index;
        }
        return maximumEnd;
    }

    private String chunkId(
            UUID artifactId,
            long artifactVersion,
            String sourceHash,
            int chunkIndex,
            int startOffset,
            int endOffset,
            String contentHash
    ) {
        return sha256(canonicalize(List.of(
                CHUNKER_VERSION,
                artifactId.toString(),
                Long.toString(artifactVersion),
                sourceHash,
                Integer.toString(chunkIndex),
                Integer.toString(startOffset),
                Integer.toString(endOffset),
                contentHash
        )));
    }

    private String canonicalize(List<String> values) {
        StringBuilder result = new StringBuilder();
        values.forEach(value -> result.append(value.length()).append(':').append(value));
        return result.toString();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}