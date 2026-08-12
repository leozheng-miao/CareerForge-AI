package com.leo.careerforgeai.memory.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.MemoryDecisionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus写入和读取memory_decision审计记录
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Mapper
public interface MemoryDecisionMapper
        extends BaseMapper<MemoryDecisionEntity> {
}