package com.leo.careerforgeai.memory.application.port;

import com.leo.careerforgeai.memory.domain.MemoryDecision;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义Memory用户决策审计记录的持久化端口
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public interface MemoryDecisionRepository {

    /** 保存一条已经通过领域校验的用户决策记录。 */
    void insert(MemoryDecision decision);

    /** 按当前用户和Memory ID读取完整决策历史。 */
    List<MemoryDecision> findByMemoryId(
            ActorId ownerId,
            UUID memoryId
    );
}