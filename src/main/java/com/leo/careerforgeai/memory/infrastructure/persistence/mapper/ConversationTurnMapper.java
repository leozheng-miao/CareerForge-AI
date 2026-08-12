package com.leo.careerforgeai.memory.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.ConversationTurnEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus写入和读取coaching_turn会话消息
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Mapper
public interface ConversationTurnMapper extends BaseMapper<ConversationTurnEntity> {

    /**
     * 查询当前owner会话内最近的消息，并按turnSequence升序返回。
     * limit通过MyBatis参数绑定，不进行SQL字符串拼接。
     */
    @Select("""
            SELECT *
            FROM (
                SELECT turn_id AS turnId,
                       session_id AS sessionId,
                       exchange_id AS exchangeId,
                       owner_id AS ownerId,
                       turn_sequence AS turnSequence,
                       turn_role AS turnRole,
                       turn_status AS turnStatus,
                       content,
                       content_hash AS contentHash,
                       agent_run_id AS agentRunId,
                       failure_code AS failureCode,
                       created_at AS createdAt
                FROM coaching_turn
                WHERE owner_id = #{ownerId}
                  AND session_id = #{sessionId}
                ORDER BY turn_sequence DESC
                LIMIT #{limit}
            ) recent_turns
            ORDER BY turnSequence ASC
            """)
    List<ConversationTurnEntity> selectRecentTurns(
            @Param("ownerId") String ownerId,
            @Param("sessionId") String sessionId,
            @Param("limit") int limit
    );
}