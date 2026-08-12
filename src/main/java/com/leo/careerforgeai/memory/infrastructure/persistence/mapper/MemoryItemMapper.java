package com.leo.careerforgeai.memory.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.MemoryItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus操作memory_item表并显式执行owner和version受控更新
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Mapper
public interface MemoryItemMapper
        extends BaseMapper<MemoryItemEntity> {

    /**
     * 同时校验Memory ID、owner和旧version后更新状态。
     * 返回1表示更新成功，返回0表示不存在、越权或版本冲突。
     */
    @Update("""
            UPDATE memory_item
            SET memory_status = #{memoryStatus},
                version = #{newVersion},
                updated_at = #{updatedAt}
            WHERE memory_id = #{memoryId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateStateIfVersionMatches(
            @Param("memoryId") String memoryId,
            @Param("ownerId") String ownerId,
            @Param("memoryStatus") String memoryStatus,
            @Param("newVersion") long newVersion,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion
    );
}