package com.leo.careerforgeai.memory.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.MemoryDecisionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus写入和读取memory_decision审计记录
 * @author: Miao Zheng
 * @date: 2026-08-12
 */
@Mapper
public interface MemoryDecisionMapper extends BaseMapper<MemoryDecisionEntity> {
    @Select("""
            SELECT COUNT(*)
            FROM memory_decision d
            INNER JOIN memory_item m
                ON m.memory_id = d.memory_id
                AND m.owner_id = d.owner_id
            WHERE d.owner_id = #{ownerId}
                AND m.memory_type = 'SKILL_EVIDENCE'
                AND d.decision_type IN ('CONFIRM', 'SUPERSEDE', 'REVOKE')
            """)
    long countSkillProfileChanges(@Param("ownerId") String ownerId);
}