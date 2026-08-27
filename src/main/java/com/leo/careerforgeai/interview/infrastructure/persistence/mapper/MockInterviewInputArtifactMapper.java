package com.leo.careerforgeai.interview.infrastructure.persistence.mapper;

import com.leo.careerforgeai.interview.infrastructure.persistence.entity.MockInterviewInputArtifactEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 保存和读取模拟面试输入快照的个人证据版本引用
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Mapper
public interface MockInterviewInputArtifactMapper {

    @Insert("""
            INSERT INTO mock_interview_input_artifact (
                input_snapshot_id, owner_id, artifact_id,
                artifact_version, artifact_source_hash,
                artifact_order, created_at
            )
            VALUES (
                #{artifact.inputSnapshotId}, #{artifact.ownerId}, #{artifact.artifactId},
                #{artifact.artifactVersion}, #{artifact.artifactSourceHash},
                #{artifact.artifactOrder}, #{artifact.createdAt}
            )
            ON DUPLICATE KEY UPDATE input_snapshot_id = input_snapshot_id
            """)
    int claim(@Param("artifact") MockInterviewInputArtifactEntity artifact);

    @Select("""
            SELECT input_snapshot_id AS inputSnapshotId,
                   owner_id AS ownerId,
                   artifact_id AS artifactId,
                   artifact_version AS artifactVersion,
                   artifact_source_hash AS artifactSourceHash,
                   artifact_order AS artifactOrder,
                   created_at AS createdAt
            FROM mock_interview_input_artifact
            WHERE input_snapshot_id = #{inputSnapshotId}
              AND owner_id = #{ownerId}
            ORDER BY artifact_order
            """)
    List<MockInterviewInputArtifactEntity> findBySnapshot(
            @Param("ownerId") String ownerId,
            @Param("inputSnapshotId") String inputSnapshotId
    );
}