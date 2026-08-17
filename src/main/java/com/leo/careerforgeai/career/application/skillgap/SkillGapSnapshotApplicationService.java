package com.leo.careerforgeai.career.application.skillgap;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapItem;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapStatus;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.application.profile.MemoryProfileQueryApplicationService;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @program: CareerForge-AI
 * @description: 基于固定岗位和技能画像版本生成、校验、保存并查询不可变能力差距快照
 * @author: Miao Zheng
 * @date: 2026-08-17
 */
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class SkillGapSnapshotApplicationService {
    private final CurrentActorProvider currentActorProvider;
    private final CareerPlanningRepository repository;
    private final MemoryProfileQueryApplicationService profileQueryService;
    private final DeterministicSkillGapMatcher matcher;
    private final Clock clock;

    public SkillGapSnapshotApplicationService(
            CurrentActorProvider currentActorProvider,
            CareerPlanningRepository repository,
            MemoryProfileQueryApplicationService profileQueryService,
            DeterministicSkillGapMatcher matcher,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.profileQueryService = Objects.requireNonNull(profileQueryService, "profileQueryService不能为空");
        this.matcher = Objects.requireNonNull(matcher, "matcher不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public SkillGapSnapshot generate(
            UUID targetRoleId,
            long expectedTargetRoleVersion,
            long expectedProfileVersion
    ) {
        Objects.requireNonNull(targetRoleId, "targetRoleId不能为空");
        if (expectedTargetRoleVersion < 1) {
            throw new IllegalArgumentException("expectedTargetRoleVersion必须从1开始");
        }
        if (expectedProfileVersion < 0) {
            throw new IllegalArgumentException("expectedProfileVersion不能小于0");
        }

        ActorId actorId = currentActor();
        TargetRole latest = repository.findLatestTargetRole(actorId)
                .orElseThrow(() -> new IllegalArgumentException("当前用户尚未确认目标岗位"));
        requireOwner(actorId, latest.ownerId(), "最新TargetRole查询结果违反owner边界");

        TargetRole targetRole = repository.findTargetRole(actorId, targetRoleId)
                .orElseThrow(() -> new IllegalArgumentException("目标岗位不存在或不属于当前用户"));
        requireOwner(actorId, targetRole.ownerId(), "TargetRole查询结果违反owner边界");
        if (targetRole.targetRoleVersion() != expectedTargetRoleVersion) {
            throw new SkillGapInputVersionConflictException("目标岗位版本已经过期，请刷新后重试");
        }

        String algorithmVersion = matcher.algorithmVersion();
        Optional<SkillGapSnapshot> existing =
                repository.findSkillGapSnapshotByInputVersions(
                        actorId,
                        targetRoleId,
                        expectedTargetRoleVersion,
                        expectedProfileVersion,
                        algorithmVersion
                );
        if (existing.isPresent()) {
            return requireExistingInput(
                    existing.get(),
                    actorId,
                    targetRole,
                    expectedProfileVersion,
                    algorithmVersion
            );
        }

        ConfirmedSkillProfile profile = profileQueryService.findConfirmedSkillProfile();
        requireOwner(actorId, profile.ownerId(), "技能画像查询结果违反owner边界");
        if (profile.profileVersion() != expectedProfileVersion) {
            throw new SkillGapInputVersionConflictException("技能画像版本已经过期，请刷新后重试");
        }

        List<GapItem> items = matcher.match(targetRole, profile);
        validateItems(targetRole, profile, items);
        SkillGapSnapshot snapshot = SkillGapSnapshot.create(
                UUID.randomUUID(), actorId, targetRoleId,
                targetRole.targetRoleVersion(), profile.profileVersion(),
                algorithmVersion, items, clock.instant()
        );
        repository.insertSkillGapSnapshot(snapshot);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public SkillGapSnapshot get(UUID snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId不能为空");
        ActorId actorId = currentActor();
        SkillGapSnapshot snapshot = repository.findSkillGapSnapshot(actorId, snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("能力差距快照不存在或不属于当前用户"));
        requireOwner(actorId, snapshot.ownerId(), "SkillGapSnapshot查询结果违反owner边界");
        return snapshot;
    }

    private void validateItems(
            TargetRole targetRole,
            ConfirmedSkillProfile profile,
            List<GapItem> items
    ) {
        Objects.requireNonNull(items, "matcher不能返回null");
        Map<String, String> requirements = SkillGapRequirementCatalog.extract(
                targetRole.requirementsSnapshot()
        );
        Map<UUID, MemoryItem> evidenceById = profile.skillEvidence().stream()
                .collect(Collectors.toMap(
                        MemoryItem::memoryId,
                        Function.identity(),
                        (first, second) -> {
                            throw new IllegalStateException("技能画像包含重复Memory ID");
                        }
                ));
        Set<String> actualRefs = new HashSet<>();

        for (GapItem item : items) {
            if (item == null) {
                throw new IllegalStateException("Gap结果包含空项");
            }
            String expectedText = requirements.get(item.requirementRef());
            if (expectedText == null) {
                throw new IllegalStateException("Gap引用了目标岗位之外的要求ID");
            }
            if (!expectedText.equals(item.requirementText())) {
                throw new IllegalStateException("Gap要求原文与TargetRole快照不一致");
            }
            if (!actualRefs.add(item.requirementRef())) {
                throw new IllegalStateException("Gap结果包含重复要求ID");
            }

            String requirementKey = MemoryNormalizedKey
                    .skillEvidence(expectedText)
                    .value();
            List<MemoryItem> referencedEvidence = item.evidenceMemoryIds()
                    .stream()
                    .map(memoryId -> {
                        MemoryItem memory = evidenceById.get(memoryId);
                        if (memory == null) {
                            throw new IllegalStateException("Gap引用了当前画像之外的Memory证据");
                        }
                        if (!memory.normalizedKey().value().equals(requirementKey)) {
                            throw new IllegalStateException("Gap引用的Memory与目标要求技能键不一致");
                        }
                        return memory;
                    })
                    .toList();

            if (item.status() == GapStatus.MATCHED
                    && referencedEvidence.stream().noneMatch(memory ->
                    memory.source().sourceType() == MemorySourceType.PROJECT_EVIDENCE)) {
                throw new IllegalStateException("MATCHED必须引用可信项目证据");
            }
            if (item.status() == GapStatus.UNVERIFIED
                    && referencedEvidence.stream().anyMatch(memory ->
                    memory.source().sourceType() == MemorySourceType.PROJECT_EVIDENCE)) {
                throw new IllegalStateException("UNVERIFIED不能忽略已有可信项目证据");
            }
            if (item.status() == GapStatus.PARTIAL) {
                throw new IllegalStateException("当前算法版本不支持PARTIAL判定");
            }
        }

        if (!actualRefs.equals(requirements.keySet())) {
            throw new IllegalStateException("Gap结果没有完整覆盖目标岗位要求ID");
        }
    }

    private SkillGapSnapshot requireExistingInput(
            SkillGapSnapshot snapshot,
            ActorId actorId,
            TargetRole targetRole,
            long expectedProfileVersion,
            String algorithmVersion
    ) {
        if (!snapshot.ownerId().equals(actorId)
                || !snapshot.targetRoleId().equals(targetRole.targetRoleId())
                || snapshot.targetRoleVersion() != targetRole.targetRoleVersion()
                || snapshot.profileVersion() != expectedProfileVersion
                || !snapshot.algorithmVersion().equals(algorithmVersion)) {
            throw new IllegalStateException("相同输入版本查询结果违反快照边界");
        }
        return snapshot;
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }

    private static void requireOwner(ActorId expected, ActorId actual, String message) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(message);
        }
    }
}