package com.leo.careerforgeai.interview.domain.evidence;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存单个个人证据的不可变正文版本、生命周期和稳定片段
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param artifactId 个人证据UUID
 * @param artifactVersion 从1开始的业务版本
 * @param ownerId 所属用户
 * @param type 证据类型
 * @param sourceName 用户可识别的来源名称
 * @param sourceHash 正文UTF-8内容的小写SHA-256
 * @param content 原始文本或Markdown正文
 * @param status 当前版本状态
 * @param supersededByVersion 替代当前版本的新版本号
 * @param chunks 当前版本的稳定证据片段
 * @param createdAt 版本创建时间
 * @param updatedAt 状态更新时间
 * @param supersededAt 被新版本替代时间
 * @param revokedAt 撤销时间
 **/
public record PersonalEvidenceArtifact(
        UUID artifactId,
        long artifactVersion,
        ActorId ownerId,
        PersonalEvidenceType type,
        String sourceName,
        String sourceHash,
        String content,
        PersonalEvidenceStatus status,
        Long supersededByVersion,
        List<Chunk> chunks,
        Instant createdAt,
        Instant updatedAt,
        Instant supersededAt,
        Instant revokedAt
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public PersonalEvidenceArtifact {
        Objects.requireNonNull(artifactId, "artifactId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(type, "type不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");

        if (artifactVersion < 1) throw new IllegalArgumentException("artifactVersion必须从1开始");
        sourceName = requireText(sourceName, "sourceName", 255);
        sourceHash = requireSha256(sourceHash, "sourceHash");
        if (content == null || content.isBlank() || content.codePointCount(0, content.length()) > 100000) {
            throw new IllegalArgumentException("content不能为空且长度不能超过100000");
        }
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt不能早于createdAt");

        chunks = validateChunks(chunks, content, createdAt);
        validateLifecycle(status, artifactVersion, supersededByVersion, createdAt, updatedAt, supersededAt, revokedAt);
    }

    public PersonalEvidenceArtifact supersede(long nextVersion, Instant now) {
        if (status != PersonalEvidenceStatus.ACTIVE) throw new IllegalStateException("只有ACTIVE版本可以被替代");
        if (nextVersion != artifactVersion + 1) throw new IllegalArgumentException("nextVersion必须是当前版本加1");
        requireTransitionTime(now);
        return new PersonalEvidenceArtifact(
                artifactId, artifactVersion, ownerId, type, sourceName, sourceHash, content,
                PersonalEvidenceStatus.SUPERSEDED, nextVersion, chunks, createdAt, now, now, null
        );
    }

    public PersonalEvidenceArtifact revoke(Instant now) {
        if (status != PersonalEvidenceStatus.ACTIVE) throw new IllegalStateException("只有ACTIVE版本可以撤销");
        requireTransitionTime(now);
        return new PersonalEvidenceArtifact(
                artifactId, artifactVersion, ownerId, type, sourceName, sourceHash, content,
                PersonalEvidenceStatus.REVOKED, null, chunks, createdAt, now, null, now
        );
    }

    private void requireTransitionTime(Instant now) {
        Objects.requireNonNull(now, "now不能为空");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("状态变更时间不能早于updatedAt");
    }

    private static List<Chunk> validateChunks(List<Chunk> chunks, String content, Instant artifactCreatedAt) {
        if (chunks == null || chunks.isEmpty()) throw new IllegalArgumentException("chunks不能为空");

        List<Chunk> copied = List.copyOf(chunks);
        Set<String> chunkIds = new HashSet<>();
        int contentLength = content.codePointCount(0, content.length());

        for (int index = 0; index < copied.size(); index++) {
            Chunk chunk = Objects.requireNonNull(copied.get(index), "chunk不能为空");
            if (chunk.chunkIndex() != index + 1) throw new IllegalArgumentException("chunkIndex必须从1开始连续递增");
            if (!chunkIds.add(chunk.evidenceChunkId())) throw new IllegalArgumentException("evidenceChunkId不能重复");
            if (chunk.endOffset() > contentLength) throw new IllegalArgumentException("chunk偏移不能超过正文长度");
            if (!sliceByCodePoints(content, chunk.startOffset(), chunk.endOffset()).equals(chunk.chunkContent())) {
                throw new IllegalArgumentException("chunkContent必须与正文偏移范围一致");
            }
            if (chunk.createdAt().isBefore(artifactCreatedAt)) {
                throw new IllegalArgumentException("chunk创建时间不能早于artifact创建时间");
            }
        }
        return copied;
    }

    private static String sliceByCodePoints(String content, int startOffset, int endOffset) {
        int startIndex = content.offsetByCodePoints(0, startOffset);
        int endIndex = content.offsetByCodePoints(0, endOffset);
        return content.substring(startIndex, endIndex);
    }

    private static void validateLifecycle(
            PersonalEvidenceStatus status,
            long version,
            Long supersededByVersion,
            Instant createdAt,
            Instant updatedAt,
            Instant supersededAt,
            Instant revokedAt
    ) {
        switch (status) {
            case ACTIVE -> {
                if (supersededByVersion != null || supersededAt != null || revokedAt != null) {
                    throw new IllegalArgumentException("ACTIVE版本不能包含替代或撤销信息");
                }
            }
            case SUPERSEDED -> {
                if (supersededByVersion == null || supersededByVersion <= version || supersededAt == null
                        || supersededAt.isBefore(createdAt) || updatedAt.isBefore(supersededAt) || revokedAt != null) {
                    throw new IllegalArgumentException("SUPERSEDED版本的生命周期字段不合法");
                }
            }
            case REVOKED -> {
                if (supersededByVersion != null || supersededAt != null || revokedAt == null
                        || revokedAt.isBefore(createdAt) || updatedAt.isBefore(revokedAt)) {
                    throw new IllegalArgumentException("REVOKED版本的生命周期字段不合法");
                }
            }
        }
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength);
        }
        return normalized;
    }

    private static String requireSha256(String value, String fieldName) {
        if (value == null || !SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "必须是64位小写SHA-256");
        }
        return value;
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存个人证据版本中可被模型安全引用的确定性正文片段
     * @author: Miao Zheng
     * @date: 2026-08-27
     * @param evidenceChunkId 片段稳定小写SHA-256标识
     * @param chunkIndex 从1开始的片段顺序
     * @param startOffset 基于Unicode码点的正文起始偏移
     * @param endOffset 基于Unicode码点的正文结束偏移
     * @param chunkContent 正文原始片段
     * @param contentHash 片段正文的小写SHA-256
     * @param createdAt 片段创建时间
     **/
    public record Chunk(
            String evidenceChunkId,
            int chunkIndex,
            int startOffset,
            int endOffset,
            String chunkContent,
            String contentHash,
            Instant createdAt
    ) {

        public Chunk {
            evidenceChunkId = requireSha256(evidenceChunkId, "evidenceChunkId");
            contentHash = requireSha256(contentHash, "contentHash");
            Objects.requireNonNull(createdAt, "createdAt不能为空");

            if (chunkIndex < 1) throw new IllegalArgumentException("chunkIndex必须从1开始");
            if (startOffset < 0 || endOffset <= startOffset) {
                throw new IllegalArgumentException("chunk偏移范围不合法");
            }
            if (chunkContent == null || chunkContent.isBlank()) {
                throw new IllegalArgumentException("chunkContent不能为空");
            }
            if (chunkContent.codePointCount(0, chunkContent.length()) != endOffset - startOffset) {
                throw new IllegalArgumentException("chunkContent长度必须等于偏移范围");
            }
            if (endOffset - startOffset > 4000) {
                throw new IllegalArgumentException("chunkContent长度不能超过4000");
            }
        }
    }
}