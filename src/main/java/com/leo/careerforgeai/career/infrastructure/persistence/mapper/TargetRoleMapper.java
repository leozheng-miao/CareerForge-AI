package com.leo.careerforgeai.career.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TargetRoleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus操作target_role目标岗位版本表
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Mapper
public interface TargetRoleMapper extends BaseMapper<TargetRoleEntity> {
}