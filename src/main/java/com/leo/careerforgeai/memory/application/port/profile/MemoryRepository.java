package com.leo.careerforgeai.memory.application.port.profile;

import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义Memory聚合的持久化端口并强制所有查询和更新携带owner边界
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public interface MemoryRepository {

    /** 保存一条服务端构造的PENDING Memory候选。 */
    void insert(MemoryItem memoryItem);

    /** 按Memory ID和当前用户共同查询，防止跨owner读取。 */
    Optional<MemoryItem> findById(
            ActorId ownerId,
            UUID memoryId
    );

    /** 查询当前用户所有仍然生效的CONFIRMED Memory。 */
    List<MemoryItem> findConfirmedByOwner(ActorId ownerId);

    /**
     * 查询同一冲突槽位或技能分组下的Memory。
     * 结果包含历史状态，用于重复检测、替代和审计。
     */
    List<MemoryItem> findByOwnerAndNormalizedKey(
            ActorId ownerId,
            MemoryType type,
            MemoryNormalizedKey normalizedKey
    );

    /**
     * 使用owner和旧version执行乐观锁更新。
     * 返回false表示记录不存在、owner不匹配或version已经过期。
     */
    boolean updateIfVersionMatches(
            ActorId ownerId,
            MemoryItem updatedMemory,
            long expectedVersion
    );
}