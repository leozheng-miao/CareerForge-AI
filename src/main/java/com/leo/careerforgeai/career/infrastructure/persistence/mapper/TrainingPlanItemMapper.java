package com.leo.careerforgeai.career.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus操作training_plan_item表并执行owner和version受控进度更新
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Mapper
public interface TrainingPlanItemMapper extends BaseMapper<TrainingPlanItemEntity> {

    /**
     * 校验计划项、所属计划、owner和旧version后更新进度与完成证据。
     */
    @Update("""
            UPDATE training_plan_item
            SET item_status = #{itemStatus},
                completion_evidence_refs_json = #{completionEvidenceRefsJson},
                version = #{newVersion},
                updated_at = #{updatedAt}
            WHERE item_id = #{itemId}
              AND plan_id = #{planId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateProgressIfVersionMatches(
            @Param("itemId") String itemId,
            @Param("planId") String planId,
            @Param("ownerId") String ownerId,
            @Param("itemStatus") String itemStatus,
            @Param("completionEvidenceRefsJson") String completionEvidenceRefsJson,
            @Param("newVersion") long newVersion,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion
    );
}