package com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowInputSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @program: CareerForge-AI
 * @description: 持久化owner隔离的面试确认输入快照
 * @author: Miao Zheng
 * @date: 2026-10-03
 **/
@Mapper
public interface InterviewFlowInputSnapshotMapper
        extends BaseMapper<InterviewFlowInputSnapshotEntity> {

    @Select("""
            SELECT input_snapshot_id AS inputSnapshotId,
                   session_id AS interviewId, owner_id AS ownerId,
                   interview_direction AS interviewDirection,
                   job_description AS jobDescription,
                   salary_range AS salaryRange,
                   personal_description AS personalDescription,
                   confirmed_profile_json AS confirmedProfileJson,
                   profile_schema_version AS profileSchemaVersion,
                   difficulty, duration_seconds AS durationSeconds,
                   input_version AS inputVersion, version,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_flow_input_snapshot
            WHERE owner_id = #{ownerId}
              AND session_id = #{interviewId}
            """)
    InterviewFlowInputSnapshotEntity selectBySession(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId
    );

    @Update("""
            UPDATE interview_flow_input_snapshot
            SET interview_direction = #{snapshot.interviewDirection},
                job_description = #{snapshot.jobDescription},
                salary_range = #{snapshot.salaryRange},
                personal_description = #{snapshot.personalDescription},
                confirmed_profile_json = #{snapshot.confirmedProfileJson},
                profile_schema_version = #{snapshot.profileSchemaVersion},
                difficulty = #{snapshot.difficulty},
                duration_seconds = #{snapshot.durationSeconds},
                input_version = #{snapshot.inputVersion},
                version = #{snapshot.version},
                updated_at = #{snapshot.updatedAt}
            WHERE input_snapshot_id = #{snapshot.inputSnapshotId}
              AND session_id = #{snapshot.interviewId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("snapshot") InterviewFlowInputSnapshotEntity snapshot,
            @Param("ownerId") String ownerId,
            @Param("expectedVersion") long expectedVersion
    );
}
