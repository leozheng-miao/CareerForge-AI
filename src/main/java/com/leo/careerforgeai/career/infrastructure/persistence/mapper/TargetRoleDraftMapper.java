package com.leo.careerforgeai.career.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TargetRoleDraftEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus操作target_role_draft岗位草案表
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@Mapper
public interface TargetRoleDraftMapper
        extends BaseMapper<TargetRoleDraftEntity> {

    /**
     * 使用owner、PENDING状态和旧version原子确认草案。
     */
    @Update("""
        UPDATE target_role_draft
        SET draft_status = 'CONFIRMED',
            version = #{newVersion},
            confirmed_target_role_id = #{targetRoleId},
            confirmed_target_role_version = #{targetRoleVersion},
            confirmed_at = #{confirmedAt}
        WHERE draft_id = #{draftId}
          AND owner_id = #{ownerId}
          AND draft_status = 'PENDING'
          AND version = #{expectedVersion}
        """)
    int confirmIfVersionMatches(
            @Param("draftId") String draftId,
            @Param("ownerId") String ownerId,
            @Param("targetRoleId") String targetRoleId,
            @Param("targetRoleVersion") long targetRoleVersion,
            @Param("newVersion") long newVersion,
            @Param("confirmedAt") Instant confirmedAt,
            @Param("expectedVersion") long expectedVersion
    );
}