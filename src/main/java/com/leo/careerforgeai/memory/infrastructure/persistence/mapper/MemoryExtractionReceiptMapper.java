package com.leo.careerforgeai.memory.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.MemoryExtractionReceiptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus操作Memory成功提取凭证表
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@Mapper
public interface MemoryExtractionReceiptMapper
        extends BaseMapper<MemoryExtractionReceiptEntity> {
}