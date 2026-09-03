package com.leo.careerforgeai.interview.infrastructure.persistence.mapper;

import com.leo.careerforgeai.interview.infrastructure.persistence.entity.PersonalEvidenceArtifactEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.PersonalEvidenceChunkEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 保存和查询个人证据版本、稳定片段并执行ACTIVE版本生命周期CAS
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Mapper
public interface PersonalEvidenceFactMapper {

    @Insert("""
            INSERT INTO personal_evidence_artifact (
                artifact_id, artifact_version, owner_id, artifact_type,
                source_name, source_hash, content, artifact_status,
                superseded_by_version, created_at, updated_at,
                superseded_at, revoked_at
            )
            VALUES (
                #{artifact.artifactId}, #{artifact.artifactVersion},
                #{artifact.ownerId}, #{artifact.artifactType},
                #{artifact.sourceName}, #{artifact.sourceHash},
                #{artifact.content}, #{artifact.artifactStatus},
                #{artifact.supersededByVersion}, #{artifact.createdAt},
                #{artifact.updatedAt}, #{artifact.supersededAt},
                #{artifact.revokedAt}
            )
            """)
    int insertArtifact(@Param("artifact") PersonalEvidenceArtifactEntity artifact);

    @Insert("""
            INSERT INTO personal_evidence_chunk (
                evidence_chunk_id, artifact_id, artifact_version, owner_id,
                chunk_index, start_offset, end_offset, chunk_content,
                content_hash, created_at
            )
            VALUES (
                #{chunk.evidenceChunkId}, #{chunk.artifactId},
                #{chunk.artifactVersion}, #{chunk.ownerId},
                #{chunk.chunkIndex}, #{chunk.startOffset},
                #{chunk.endOffset}, #{chunk.chunkContent},
                #{chunk.contentHash}, #{chunk.createdAt}
            )
            """)
    int insertChunk(@Param("chunk") PersonalEvidenceChunkEntity chunk);

    @Select("""
            SELECT artifact_id AS artifactId,
                   artifact_version AS artifactVersion,
                   owner_id AS ownerId,
                   artifact_type AS artifactType,
                   source_name AS sourceName,
                   source_hash AS sourceHash,
                   content,
                   artifact_status AS artifactStatus,
                   superseded_by_version AS supersededByVersion,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   superseded_at AS supersededAt,
                   revoked_at AS revokedAt
            FROM personal_evidence_artifact
            WHERE owner_id = #{ownerId}
              AND artifact_id = #{artifactId}
              AND artifact_status = 'ACTIVE'
            """)
    PersonalEvidenceArtifactEntity findActive(
            @Param("ownerId") String ownerId,
            @Param("artifactId") String artifactId
    );

    @Select("""
            SELECT artifact_id AS artifactId,
                   artifact_version AS artifactVersion,
                   owner_id AS ownerId,
                   artifact_type AS artifactType,
                   source_name AS sourceName,
                   source_hash AS sourceHash,
                   content,
                   artifact_status AS artifactStatus,
                   superseded_by_version AS supersededByVersion,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   superseded_at AS supersededAt,
                   revoked_at AS revokedAt
            FROM personal_evidence_artifact
            WHERE owner_id = #{ownerId}
              AND artifact_id = #{artifactId}
              AND artifact_version = #{artifactVersion}
            """)
    PersonalEvidenceArtifactEntity findVersion(
            @Param("ownerId") String ownerId,
            @Param("artifactId") String artifactId,
            @Param("artifactVersion") long artifactVersion
    );

    @Select("""
            SELECT evidence_chunk_id AS evidenceChunkId,
                   artifact_id AS artifactId,
                   artifact_version AS artifactVersion,
                   owner_id AS ownerId,
                   chunk_index AS chunkIndex,
                   start_offset AS startOffset,
                   end_offset AS endOffset,
                   chunk_content AS chunkContent,
                   content_hash AS contentHash,
                   created_at AS createdAt
            FROM personal_evidence_chunk
            WHERE owner_id = #{ownerId}
              AND artifact_id = #{artifactId}
              AND artifact_version = #{artifactVersion}
            ORDER BY chunk_index
            """)
    List<PersonalEvidenceChunkEntity> findChunks(
            @Param("ownerId") String ownerId,
            @Param("artifactId") String artifactId,
            @Param("artifactVersion") long artifactVersion
    );

    @Select("""
            SELECT artifact_version
            FROM personal_evidence_artifact
            WHERE owner_id = #{ownerId}
              AND artifact_id = #{artifactId}
              AND artifact_status = 'ACTIVE'
            FOR UPDATE
            """)
    List<Long> lockActiveVersions(
            @Param("ownerId") String ownerId,
            @Param("artifactId") String artifactId
    );

    @Update("""
            UPDATE personal_evidence_artifact
            SET artifact_status = #{artifact.artifactStatus},
                superseded_by_version = #{artifact.supersededByVersion},
                updated_at = #{artifact.updatedAt},
                superseded_at = #{artifact.supersededAt},
                revoked_at = #{artifact.revokedAt}
            WHERE owner_id = #{artifact.ownerId}
              AND artifact_id = #{artifact.artifactId}
              AND artifact_version = #{expectedVersion}
              AND artifact_status = 'ACTIVE'
            """)
    int updateActiveLifecycle(
            @Param("artifact") PersonalEvidenceArtifactEntity artifact,
            @Param("expectedVersion") long expectedVersion
    );

    @Select("""
        SELECT artifact_id AS artifactId,
               artifact_version AS artifactVersion,
               owner_id AS ownerId,
               artifact_type AS artifactType,
               source_name AS sourceName,
               source_hash AS sourceHash,
               content,
               artifact_status AS artifactStatus,
               superseded_by_version AS supersededByVersion,
               created_at AS createdAt,
               updated_at AS updatedAt,
               superseded_at AS supersededAt,
               revoked_at AS revokedAt
        FROM personal_evidence_artifact
        WHERE owner_id = #{ownerId}
          AND artifact_id = #{artifactId}
          AND artifact_version = #{artifactVersion}
        FOR SHARE
        """)
    PersonalEvidenceArtifactEntity lockVersionForSnapshot(
            @Param("ownerId") String ownerId,
            @Param("artifactId") String artifactId,
            @Param("artifactVersion") long artifactVersion
    );

    @Select("""
        <script>
        SELECT artifact_id AS artifactId,
               artifact_version AS artifactVersion,
               artifact_type AS artifactType,
               source_name AS sourceName,
               created_at AS createdAt,
               updated_at AS updatedAt
        FROM personal_evidence_artifact
        WHERE owner_id = #{ownerId}
          AND artifact_status = 'ACTIVE'
        <if test="artifactType != null">
          AND artifact_type = #{artifactType}
        </if>
        <if test="beforeUpdatedAt != null">
          AND (
            updated_at &lt; #{beforeUpdatedAt}
            OR (updated_at = #{beforeUpdatedAt} AND artifact_id &lt; #{beforeArtifactId})
          )
        </if>
        ORDER BY updated_at DESC, artifact_id DESC
        LIMIT #{limit}
        </script>
        """)
    List<PersonalEvidenceArtifactEntity> findActivePage(
            @Param("ownerId") String ownerId,
            @Param("artifactType") String artifactType,
            @Param("beforeUpdatedAt") Instant beforeUpdatedAt,
            @Param("beforeArtifactId") String beforeArtifactId,
            @Param("limit") int limit
    );
}