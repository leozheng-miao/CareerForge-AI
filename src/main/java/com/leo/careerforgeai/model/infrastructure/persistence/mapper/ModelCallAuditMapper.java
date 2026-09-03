package com.leo.careerforgeai.model.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.model.infrastructure.persistence.entity.ModelCallAuditEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus写入模型调用审计表。
 * @author: Miao Zheng
 * @date: 2026-09-03
 */
@Mapper
public interface ModelCallAuditMapper extends BaseMapper<ModelCallAuditEntity> {
}
