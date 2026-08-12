package com.leo.careerforgeai.career.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.career.infrastructure.persistence.converter.CareerPlanningPersistenceConverter;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.SkillGapSnapshotEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TargetRoleEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanItemEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.mapper.SkillGapSnapshotMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.mapper.TargetRoleMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.mapper.TrainingPlanItemMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.mapper.TrainingPlanMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus实现目标岗位、能力差距和训练计划持久化端口
 * 关键事务顺序：
 * 读取当前完整计划
 * → 主表按owner + planId + version抢占更新权
 * → 更新发生变化的计划项
 * → 全部成功后提交
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MyBatisPlusCareerPlanningAdapter implements CareerPlanningRepository {

    private final TargetRoleMapper targetRoleMapper;
    private final SkillGapSnapshotMapper gapSnapshotMapper;
    private final TrainingPlanMapper trainingPlanMapper;
    private final TrainingPlanItemMapper trainingPlanItemMapper;
    private final CareerPlanningPersistenceConverter converter;

    public MyBatisPlusCareerPlanningAdapter(
            TargetRoleMapper targetRoleMapper,
            SkillGapSnapshotMapper gapSnapshotMapper,
            TrainingPlanMapper trainingPlanMapper,
            TrainingPlanItemMapper trainingPlanItemMapper,
            CareerPlanningPersistenceConverter converter
    ) {
        this.targetRoleMapper = Objects.requireNonNull(targetRoleMapper, "targetRoleMapper 不能为空");
        this.gapSnapshotMapper = Objects.requireNonNull(gapSnapshotMapper, "gapSnapshotMapper 不能为空");
        this.trainingPlanMapper = Objects.requireNonNull(trainingPlanMapper, "trainingPlanMapper 不能为空");
        this.trainingPlanItemMapper = Objects.requireNonNull(trainingPlanItemMapper, "trainingPlanItemMapper 不能为空");
        this.converter = Objects.requireNonNull(converter, "converter 不能为空");
    }

    @Override
    public void insertTargetRole(TargetRole targetRole) {
        Objects.requireNonNull(targetRole, "targetRole 不能为空");
        int affectedRows = targetRoleMapper.insert(converter.toEntity(targetRole));
        requireSingleAffectedRow(affectedRows, "插入TargetRole失败");
    }

    @Override
    public Optional<TargetRole> findTargetRole(ActorId ownerId, UUID targetRoleId) {
        requireOwnerAndId(ownerId, targetRoleId, "targetRoleId");

        LambdaQueryWrapper<TargetRoleEntity> query = new LambdaQueryWrapper<>();
        query.eq(TargetRoleEntity::getOwnerId, ownerId.value())
                .eq(TargetRoleEntity::getTargetRoleId, targetRoleId.toString());

        return Optional.ofNullable(targetRoleMapper.selectOne(query)).map(converter::toDomain);
    }

    @Override
    public Optional<TargetRole> findLatestTargetRole(ActorId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");

        LambdaQueryWrapper<TargetRoleEntity> query = new LambdaQueryWrapper<>();
        query.eq(TargetRoleEntity::getOwnerId, ownerId.value())
                .orderByDesc(TargetRoleEntity::getTargetRoleVersion)
                .last("LIMIT 1");

        return Optional.ofNullable(targetRoleMapper.selectOne(query)).map(converter::toDomain);
    }

    @Override
    public void insertSkillGapSnapshot(SkillGapSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
        int affectedRows = gapSnapshotMapper.insert(converter.toEntity(snapshot));
        requireSingleAffectedRow(affectedRows, "插入SkillGapSnapshot失败");
    }

    @Override
    public Optional<SkillGapSnapshot> findSkillGapSnapshot(ActorId ownerId, UUID snapshotId) {
        requireOwnerAndId(ownerId, snapshotId, "snapshotId");

        LambdaQueryWrapper<SkillGapSnapshotEntity> query = new LambdaQueryWrapper<>();
        query.eq(SkillGapSnapshotEntity::getOwnerId, ownerId.value())
                .eq(SkillGapSnapshotEntity::getSnapshotId, snapshotId.toString());

        return Optional.ofNullable(gapSnapshotMapper.selectOne(query)).map(converter::toDomain);
    }

    @Override
    public Optional<SkillGapSnapshot> findSkillGapSnapshotByInputVersions(
            ActorId ownerId,
            UUID targetRoleId,
            long targetRoleVersion,
            long profileVersion
    ) {
        requireOwnerAndId(ownerId, targetRoleId, "targetRoleId");

        if (targetRoleVersion < 1) {
            throw new IllegalArgumentException("targetRoleVersion必须从1开始");
        }
        if (profileVersion < 0) {
            throw new IllegalArgumentException("profileVersion不能小于0");
        }

        LambdaQueryWrapper<SkillGapSnapshotEntity> query = new LambdaQueryWrapper<>();
        query.eq(SkillGapSnapshotEntity::getOwnerId, ownerId.value())
                .eq(SkillGapSnapshotEntity::getTargetRoleId, targetRoleId.toString())
                .eq(SkillGapSnapshotEntity::getTargetRoleVersion, targetRoleVersion)
                .eq(SkillGapSnapshotEntity::getProfileVersion, profileVersion);

        return Optional.ofNullable(gapSnapshotMapper.selectOne(query)).map(converter::toDomain);
    }

    /**
     * 主计划与全部计划项必须一起写入。
     * 任意一条计划项插入失败时，事务回滚主计划。
     */
    @Override
    @Transactional
    public void insertTrainingPlan(TrainingPlan trainingPlan) {
        Objects.requireNonNull(trainingPlan, "trainingPlan 不能为空");

        int planAffectedRows = trainingPlanMapper.insert(converter.toEntity(trainingPlan));
        requireSingleAffectedRow(planAffectedRows, "插入TrainingPlan失败");

        for (TrainingPlanItem item : trainingPlan.items()) {
            TrainingPlanItemEntity entity = converter.toEntity(
                    trainingPlan.ownerId(),
                    trainingPlan.planId(),
                    item
            );

            int itemAffectedRows = trainingPlanItemMapper.insert(entity);
            requireSingleAffectedRow(itemAffectedRows, "插入TrainingPlanItem失败");
        }
    }

    @Override
    public Optional<TrainingPlan> findTrainingPlan(ActorId ownerId, UUID planId) {
        requireOwnerAndId(ownerId, planId, "planId");

        LambdaQueryWrapper<TrainingPlanEntity> query = new LambdaQueryWrapper<>();
        query.eq(TrainingPlanEntity::getOwnerId, ownerId.value())
                .eq(TrainingPlanEntity::getPlanId, planId.toString());

        TrainingPlanEntity planEntity = trainingPlanMapper.selectOne(query);

        if (planEntity == null) {
            return Optional.empty();
        }

        return Optional.of(loadCompletePlan(planEntity));
    }

    @Override
    public Optional<TrainingPlan> findLatestTrainingPlan(ActorId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");

        LambdaQueryWrapper<TrainingPlanEntity> query = new LambdaQueryWrapper<>();
        query.eq(TrainingPlanEntity::getOwnerId, ownerId.value())
                .orderByDesc(TrainingPlanEntity::getPlanVersion)
                .last("LIMIT 1");

        TrainingPlanEntity planEntity = trainingPlanMapper.selectOne(query);

        if (planEntity == null) {
            return Optional.empty();
        }

        return Optional.of(loadCompletePlan(planEntity));
    }

    /**
     * 先用计划主表version争抢聚合更新权，再更新发生变化的计划项。
     * 计划项更新失败时抛出异常，使主表更新一并回滚。
     */
    @Override
    @Transactional
    public boolean updateTrainingPlanIfVersionMatches(
            ActorId ownerId,
            TrainingPlan updatedPlan,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(updatedPlan, "updatedPlan 不能为空");

        if (!ownerId.equals(updatedPlan.ownerId())) {
            throw new IllegalArgumentException("ownerId与TrainingPlan归属不一致");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion不能小于0");
        }
        if (updatedPlan.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("更新后的计划version必须比旧version增加1");
        }

        Optional<TrainingPlan> currentResult = findTrainingPlan(ownerId, updatedPlan.planId());

        if (currentResult.isEmpty() || currentResult.get().version() != expectedVersion) {
            return false;
        }

        TrainingPlan currentPlan = currentResult.get();
        validatePlanUpdate(currentPlan, updatedPlan);

        int planAffectedRows = trainingPlanMapper.updateStateIfVersionMatches(
                updatedPlan.planId().toString(),
                ownerId.value(),
                updatedPlan.status().name(),
                updatedPlan.version(),
                updatedPlan.updatedAt(),
                updatedPlan.activatedAt(),
                updatedPlan.completedAt(),
                updatedPlan.cancelledAt(),
                expectedVersion
        );

        if (planAffectedRows > 1) {
            throw new IllegalStateException("TrainingPlan乐观锁更新影响了多行数据");
        }
        if (planAffectedRows == 0) {
            return false;
        }

        updateChangedItems(ownerId, currentPlan, updatedPlan);
        return true;
    }

    private TrainingPlan loadCompletePlan(TrainingPlanEntity planEntity) {
        LambdaQueryWrapper<TrainingPlanItemEntity> itemQuery = new LambdaQueryWrapper<>();
        itemQuery.eq(TrainingPlanItemEntity::getOwnerId, planEntity.getOwnerId())
                .eq(TrainingPlanItemEntity::getPlanId, planEntity.getPlanId())
                .orderByAsc(TrainingPlanItemEntity::getWeekNumber)
                .orderByAsc(TrainingPlanItemEntity::getItemId);

        List<TrainingPlanItemEntity> itemEntities = trainingPlanItemMapper.selectList(itemQuery);
        return converter.toDomain(planEntity, itemEntities);
    }

    private void updateChangedItems(
            ActorId ownerId,
            TrainingPlan currentPlan,
            TrainingPlan updatedPlan
    ) {
        Map<UUID, TrainingPlanItem> currentItems = new HashMap<>();

        for (TrainingPlanItem currentItem : currentPlan.items()) {
            currentItems.put(currentItem.itemId(), currentItem);
        }

        for (TrainingPlanItem updatedItem : updatedPlan.items()) {
            TrainingPlanItem currentItem = currentItems.get(updatedItem.itemId());

            if (currentItem == null) {
                throw new IllegalArgumentException("更新后的计划包含未知计划项");
            }
            if (updatedItem.equals(currentItem)) {
                continue;
            }

            validateItemUpdate(currentItem, updatedItem);

            TrainingPlanItemEntity updatedEntity = converter.toEntity(
                    ownerId,
                    updatedPlan.planId(),
                    updatedItem
            );

            int affectedRows = trainingPlanItemMapper.updateProgressIfVersionMatches(
                    updatedEntity.getItemId(),
                    updatedEntity.getPlanId(),
                    updatedEntity.getOwnerId(),
                    updatedEntity.getItemStatus(),
                    updatedEntity.getCompletionEvidenceRefsJson(),
                    updatedItem.version(),
                    updatedItem.updatedAt(),
                    currentItem.version()
            );

            if (affectedRows != 1) {
                throw new IllegalStateException(
                        "TrainingPlanItem并发更新冲突，事务必须回滚"
                );
            }
        }
    }

    private static void validatePlanUpdate(
            TrainingPlan currentPlan,
            TrainingPlan updatedPlan
    ) {
        boolean immutableFieldsChanged =
                !currentPlan.planId().equals(updatedPlan.planId())
                        || !currentPlan.ownerId().equals(updatedPlan.ownerId())
                        || currentPlan.planVersion() != updatedPlan.planVersion()
                        || !currentPlan.gapSnapshotId().equals(updatedPlan.gapSnapshotId())
                        || !currentPlan.title().equals(updatedPlan.title())
                        || !currentPlan.createdAt().equals(updatedPlan.createdAt());

        if (immutableFieldsChanged) {
            throw new IllegalArgumentException("TrainingPlan不可变字段不能修改");
        }
        if (currentPlan.items().size() != updatedPlan.items().size()) {
            throw new IllegalArgumentException("训练计划更新不能增加或删除计划项");
        }
    }

    private static void validateItemUpdate(
            TrainingPlanItem currentItem,
            TrainingPlanItem updatedItem
    ) {
        boolean immutableFieldsChanged =
                currentItem.weekNumber() != updatedItem.weekNumber()
                        || !currentItem.title().equals(updatedItem.title())
                        || !currentItem.taskDescription().equals(updatedItem.taskDescription())
                        || currentItem.estimatedMinutes() != updatedItem.estimatedMinutes()
                        || !currentItem.completionCriteria().equals(updatedItem.completionCriteria())
                        || !currentItem.evidenceRequirement().equals(updatedItem.evidenceRequirement())
                        || !currentItem.gapItemIds().equals(updatedItem.gapItemIds())
                        || !Objects.equals(currentItem.foundationGoal(), updatedItem.foundationGoal())
                        || !currentItem.resourceRefs().equals(updatedItem.resourceRefs())
                        || !currentItem.createdAt().equals(updatedItem.createdAt());

        if (immutableFieldsChanged) {
            throw new IllegalArgumentException("TrainingPlanItem不可变字段不能修改");
        }
        if (updatedItem.version() != currentItem.version() + 1) {
            throw new IllegalArgumentException("发生变化的计划项version必须增加1");
        }

        boolean progressUnchanged =
                currentItem.status() == updatedItem.status()
                        && currentItem.completionEvidenceRefs()
                        .equals(updatedItem.completionEvidenceRefs());

        if (progressUnchanged) {
            throw new IllegalArgumentException("计划项version增加但进度没有变化");
        }
    }

    private static void requireOwnerAndId(ActorId ownerId, UUID id, String fieldName) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(id, fieldName + " 不能为空");
    }

    private static void requireSingleAffectedRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message + ": affectedRows=" + affectedRows);
        }
    }
}