package com.leo.careerforgeai.career.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus操作training_plan表并执行owner和version受控更新
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Mapper
public interface TrainingPlanMapper extends BaseMapper<TrainingPlanEntity> {

    /**
     * 同时校验计划ID、owner和旧version后更新计划生命周期。
     */
    @Update("""
            UPDATE training_plan
            SET plan_status = #{planStatus},
                version = #{newVersion},
                updated_at = #{updatedAt},
                activated_at = #{activatedAt},
                completed_at = #{completedAt},
                cancelled_at = #{cancelledAt}
            WHERE plan_id = #{planId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateStateIfVersionMatches(
            @Param("planId") String planId,
            @Param("ownerId") String ownerId,
            @Param("planStatus") String planStatus,
            @Param("newVersion") long newVersion,
            @Param("updatedAt") Instant updatedAt,
            @Param("activatedAt") Instant activatedAt,
            @Param("completedAt") Instant completedAt,
            @Param("cancelledAt") Instant cancelledAt,
            @Param("expectedVersion") long expectedVersion
    );
}