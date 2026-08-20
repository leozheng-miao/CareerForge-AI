package com.leo.careerforgeai.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.agent.infrastructure.persistence.entity.CoachingRunEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus操作coaching_run并执行owner和version受控状态更新
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Mapper
public interface CoachingRunMapper
        extends BaseMapper<CoachingRunEntity> {

    @Update("""
            UPDATE coaching_run
            SET run_status = #{runStatus},
                user_turn_id = #{userTurnId},
                assistant_turn_id = #{assistantTurnId},
                failure_code = #{failureCode},
                version = #{newVersion},
                accepted_at = #{acceptedAt},
                started_at = #{startedAt},
                finished_at = #{finishedAt},
                updated_at = #{updatedAt}
            WHERE run_id = #{runId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("runId") String runId,
            @Param("ownerId") String ownerId,
            @Param("runStatus") String runStatus,
            @Param("userTurnId") String userTurnId,
            @Param("assistantTurnId") String assistantTurnId,
            @Param("failureCode") String failureCode,
            @Param("newVersion") long newVersion,
            @Param("acceptedAt") Instant acceptedAt,
            @Param("startedAt") Instant startedAt,
            @Param("finishedAt") Instant finishedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion
    );

    @Insert("""
        INSERT INTO coaching_run (
            run_id,
            owner_id,
            session_id,
            request_id,
            request_fingerprint,
            expected_session_version,
            run_status,
            user_turn_id,
            assistant_turn_id,
            failure_code,
            version,
            accepted_at,
            started_at,
            finished_at,
            created_at,
            updated_at
        )
        VALUES (
            #{run.runId},
            #{run.ownerId},
            #{run.sessionId},
            #{run.requestId},
            #{run.requestFingerprint},
            #{run.expectedSessionVersion},
            #{run.runStatus},
            #{run.userTurnId},
            #{run.assistantTurnId},
            #{run.failureCode},
            #{run.version},
            #{run.acceptedAt},
            #{run.startedAt},
            #{run.finishedAt},
            #{run.createdAt},
            #{run.updatedAt}
        )
        ON DUPLICATE KEY UPDATE run_id = run_id
        """)
    int claim(@Param("run") CoachingRunEntity run);
}