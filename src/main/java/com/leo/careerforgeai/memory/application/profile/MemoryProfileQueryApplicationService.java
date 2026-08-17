package com.leo.careerforgeai.memory.application.profile;

import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 查询当前用户待确认候选和有效长期画像并复核Repository返回边界
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MemoryProfileQueryApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final MemoryRepository memoryRepository;

    public MemoryProfileQueryApplicationService(
            CurrentActorProvider currentActorProvider,
            MemoryRepository memoryRepository
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.memoryRepository = Objects.requireNonNull(memoryRepository, "memoryRepository不能为空");
    }

    /** 查询当前用户全部PENDING候选，不接受客户端owner或status参数。 */
    @Transactional(readOnly = true)
    public List<MemoryItem> findPendingCandidates() {
        ActorId actorId = currentActorProvider.currentActor();
        return requireOwnedStatus(
                memoryRepository.findPendingByOwner(actorId),
                actorId,
                MemoryStatus.PENDING
        );
    }

    /** 查询当前用户全部有效长期Memory，只允许返回CONFIRMED状态。 */
    @Transactional(readOnly = true)
    public List<MemoryItem> findConfirmedProfile() {
        ActorId actorId = currentActorProvider.currentActor();
        return requireOwnedStatus(
                memoryRepository.findConfirmedByOwner(actorId),
                actorId,
                MemoryStatus.CONFIRMED
        );
    }

    private static List<MemoryItem> requireOwnedStatus(
            List<MemoryItem> memories,
            ActorId actorId,
            MemoryStatus requiredStatus
    ) {
        Objects.requireNonNull(memories, "memoryRepository不能返回null");
        if (memories.stream().anyMatch(memory ->
                memory == null
                        || !actorId.equals(memory.ownerId())
                        || memory.status() != requiredStatus)) {
            throw new IllegalStateException(
                    requiredStatus + " Memory查询结果违反owner或状态边界"
            );
        }
        return List.copyOf(memories);
    }

    /** 读取当前用户确定版本的全部CONFIRMED技能证据。 */
    @Transactional(readOnly = true)
    public ConfirmedSkillProfile findConfirmedSkillProfile() {
        ActorId actorId = Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
        long versionBefore = memoryRepository.countSkillProfileChanges(actorId);
        List<MemoryItem> skillEvidence = requireOwnedStatus(
                memoryRepository.findConfirmedByOwner(actorId),
                actorId,
                MemoryStatus.CONFIRMED
        ).stream().filter(memory -> memory.type() == MemoryType.SKILL_EVIDENCE).toList();
        long versionAfter = memoryRepository.countSkillProfileChanges(actorId);
        if (versionBefore != versionAfter) {
            throw new IllegalStateException("技能画像读取期间发生变化，请重试");
        }
        return new ConfirmedSkillProfile(actorId, versionAfter, skillEvidence);
    }
}