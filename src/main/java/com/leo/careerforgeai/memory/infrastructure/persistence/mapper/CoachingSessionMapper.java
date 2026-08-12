package com.leo.careerforgeai.memory.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.CoachingSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus操作coaching_session并执行owner和version受控更新
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Mapper
public interface CoachingSessionMapper extends BaseMapper<CoachingSessionEntity> {

    /**
     * 同时校验sessionId、ownerId和旧version后更新会话。
     * 返回0表示会话不存在、越权或发生并发冲突。
     */
    @Update("""
            UPDATE coaching_session
            SET session_status = #{sessionStatus},
                next_turn_sequence = #{nextTurnSequence},
                version = #{newVersion},
                updated_at = #{updatedAt},
                closed_at = #{closedAt}
            WHERE session_id = #{sessionId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("sessionId") String sessionId,
            @Param("ownerId") String ownerId,
            @Param("sessionStatus") String sessionStatus,
            @Param("nextTurnSequence") long nextTurnSequence,
            @Param("newVersion") long newVersion,
            @Param("updatedAt") Instant updatedAt,
            @Param("closedAt") Instant closedAt,
            @Param("expectedVersion") long expectedVersion
    );
}