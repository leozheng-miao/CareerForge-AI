package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TargetRoleDraft;

import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用当前Actor确认岗位草案、创建不可变TargetRole版本并提供受控查询
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@Service
@ConditionalOnBean(CareerPlanningRepository.class)
public class TargetRoleApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CareerPlanningRepository repository;
    private final Clock clock;

    public TargetRoleApplicationService(CurrentActorProvider currentActorProvider, CareerPlanningRepository repository, Clock clock) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public TargetRole confirmDraft(UUID draftId, long expectedVersion) {
        Objects.requireNonNull(draftId, "draftId不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion不能小于0");
        }
        ActorId actorId = currentActor();
        TargetRoleDraft draft = requireOwnedDraft(actorId, draftId);
        if (draft.status() == TargetRoleDraft.Status.CONFIRMED) {
            return requireOwnedTargetRole(actorId, draft.confirmedTargetRoleId());
        }
        if (draft.version() != expectedVersion) {
            throw new TargetRoleVersionConflictException("目标岗位草案版本已经过期，请刷新后重试");
        }
        long targetRoleVersion = nextTargetRoleVersion(actorId);
        Instant confirmedAt = clock.instant();
        TargetRole targetRole = TargetRole.createConfirmed(
                UUID.randomUUID(), actorId, targetRoleVersion, draft.sourceRef(), draft.sourceHash(),
                draft.parserVersion(), draft.promptVersion(), draft.requirementsSnapshot(), confirmedAt
        );
        TargetRoleDraft confirmedDraft = draft.confirm(targetRole, confirmedAt);
        repository.confirmTargetRoleDraft(actorId, confirmedDraft, targetRole, expectedVersion);
        return targetRole;
    }

    @Transactional(readOnly = true)
    public TargetRole get(UUID targetRoleId) {
        Objects.requireNonNull(targetRoleId, "targetRoleId不能为空");
        ActorId actorId = currentActor();
        TargetRole targetRole = repository.findTargetRole(actorId, targetRoleId)
                .orElseThrow(() -> new IllegalArgumentException("目标岗位不存在或不属于当前用户"));
        return requireOwnedTargetRoleResult(actorId, targetRole);
    }

    @Transactional(readOnly = true)
    public TargetRole getLatest() {
        ActorId actorId = currentActor();
        TargetRole targetRole = repository.findLatestTargetRole(actorId)
                .orElseThrow(() -> new IllegalArgumentException("当前用户尚未确认目标岗位"));
        return requireOwnedTargetRoleResult(actorId, targetRole);
    }

    private long nextTargetRoleVersion(ActorId actorId) {
        return repository.findLatestTargetRole(actorId)
                .map(targetRole -> {
                    requireOwnedTargetRoleResult(actorId, targetRole);
                    return Math.addExact(targetRole.targetRoleVersion(), 1);
                })
                .orElse(1L);
    }

    private TargetRoleDraft requireOwnedDraft(ActorId actorId, UUID draftId) {
        TargetRoleDraft draft = repository.findTargetRoleDraft(actorId, draftId)
                .orElseThrow(() -> new IllegalArgumentException("目标岗位草案不存在或不属于当前用户"));
        if (!draft.ownerId().equals(actorId)) {
            throw new IllegalStateException("目标岗位草案查询结果违反owner边界");
        }
        return draft;
    }

    private TargetRole requireOwnedTargetRole(ActorId actorId, UUID targetRoleId) {
        if (targetRoleId == null) {
            throw new IllegalStateException("已确认草案缺少TargetRole引用");
        }
        TargetRole targetRole = repository.findTargetRole(actorId, targetRoleId)
                .orElseThrow(() -> new IllegalStateException("已确认草案关联的TargetRole不存在"));
        return requireOwnedTargetRoleResult(actorId, targetRole);
    }

    private static TargetRole requireOwnedTargetRoleResult(ActorId actorId, TargetRole targetRole) {
        if (!targetRole.ownerId().equals(actorId)) {
            throw new IllegalStateException("TargetRole查询结果违反owner边界");
        }
        return targetRole;
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}