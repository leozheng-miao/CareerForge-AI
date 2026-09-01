package com.leo.careerforgeai.interview.api.dto.evidence;

import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceStatus;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回个人证据版本及其稳定片段，不暴露owner内部标识
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param artifactId 个人证据ID
 * @param artifactVersion 当前业务版本
 * @param type 证据类型
 * @param sourceName 材料名称
 * @param sourceHash 规范化正文Hash
 * @param content 规范化文本或Markdown正文
 * @param status 当前版本状态
 * @param supersededByVersion 替代当前版本的新版本号
 * @param chunks 稳定证据片段
 * @param createdAt 版本创建时间
 * @param updatedAt 生命周期更新时间
 * @param supersededAt 被替代时间
 * @param revokedAt 撤销时间
 **/
public record PersonalEvidenceResponse(
        UUID artifactId,
        long artifactVersion,
        PersonalEvidenceType type,
        String sourceName,
        String sourceHash,
        String content,
        PersonalEvidenceStatus status,
        Long supersededByVersion,
        List<ChunkResponse> chunks,
        Instant createdAt,
        Instant updatedAt,
        Instant supersededAt,
        Instant revokedAt
) {

    public static PersonalEvidenceResponse from(PersonalEvidenceArtifact artifact) {
        return new PersonalEvidenceResponse(
                artifact.artifactId(),
                artifact.artifactVersion(),
                artifact.type(),
                artifact.sourceName(),
                artifact.sourceHash(),
                artifact.content(),
                artifact.status(),
                artifact.supersededByVersion(),
                artifact.chunks().stream().map(ChunkResponse::from).toList(),
                artifact.createdAt(),
                artifact.updatedAt(),
                artifact.supersededAt(),
                artifact.revokedAt()
        );
    }

    /**
     * @program: CareerForge-AI
     * @description: 返回可供当前用户查看和后续面试快照引用的稳定证据片段
     * @author: Miao Zheng
     * @date: 2026-08-27
     * @param evidenceChunkId 稳定片段ID
     * @param chunkIndex 从1开始的片段顺序
     * @param startOffset Unicode码点起始偏移
     * @param endOffset Unicode码点结束偏移
     * @param chunkContent 原始正文片段
     * @param contentHash 片段正文Hash
     * @param createdAt 创建时间
     **/
    public record ChunkResponse(
            String evidenceChunkId,
            int chunkIndex,
            int startOffset,
            int endOffset,
            String chunkContent,
            String contentHash,
            Instant createdAt
    ) {

        public static ChunkResponse from(PersonalEvidenceArtifact.Chunk chunk) {
            return new ChunkResponse(
                    chunk.evidenceChunkId(),
                    chunk.chunkIndex(),
                    chunk.startOffset(),
                    chunk.endOffset(),
                    chunk.chunkContent(),
                    chunk.contentHash(),
                    chunk.createdAt()
            );
        }
    }
}