package com.leo.careerforgeai.career.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.SkillGapSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus操作skill_gap_snapshot能力差距快照表
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Mapper
public interface SkillGapSnapshotMapper extends BaseMapper<SkillGapSnapshotEntity> {
}