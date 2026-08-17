package com.leo.careerforgeai.career.infrastructure.persistence.converter;

import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TargetRoleDraft;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.SkillGapSnapshotEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TargetRoleDraftEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TargetRoleEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanItemEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在求职规划领域对象和MyBatis-Plus数据库Entity之间执行受控转换
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Component
public class CareerPlanningPersistenceConverter {

    private static final TypeReference<List<SkillGapSnapshot.GapItem>> GAP_ITEM_LIST_TYPE =
            new TypeReference<>() {
            };

    private static final TypeReference<List<UUID>> UUID_LIST_TYPE =
            new TypeReference<>() {
            };

    private static final TypeReference<List<TrainingPlanItem.ResourceRef>> RESOURCE_REF_LIST_TYPE =
            new TypeReference<>() {
            };

    private static final TypeReference<List<String>> STRING_LIST_TYPE =
            new TypeReference<>() {
            };

    private final JsonMapper jsonMapper;

    public CareerPlanningPersistenceConverter(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper 不能为空");
    }

    /** 将目标岗位领域对象转换为数据库Entity。 */
    public TargetRoleEntity toEntity(TargetRole targetRole) {
        Objects.requireNonNull(targetRole, "targetRole 不能为空");

        TargetRoleEntity entity = new TargetRoleEntity();
        entity.setTargetRoleId(targetRole.targetRoleId().toString());
        entity.setOwnerId(targetRole.ownerId().value());
        entity.setTargetRoleVersion(targetRole.targetRoleVersion());
        entity.setSourceRef(targetRole.sourceRef());
        entity.setSourceHash(targetRole.sourceHash());
        entity.setParserVersion(targetRole.parserVersion());
        entity.setPromptVersion(targetRole.promptVersion());
        entity.setRequirementsJson(serialize(targetRole.requirementsSnapshot(), "requirements"));
        entity.setConfirmedAt(targetRole.confirmedAt());
        return entity;
    }

    /** 将数据库Entity还原为目标岗位领域对象。 */
    public TargetRole toDomain(TargetRoleEntity entity) {
        Objects.requireNonNull(entity, "entity 不能为空");

        return new TargetRole(
                UUID.fromString(entity.getTargetRoleId()),
                new ActorId(entity.getOwnerId()),
                requireLong(entity.getTargetRoleVersion(), "targetRoleVersion"),
                entity.getSourceRef(),
                entity.getSourceHash(),
                entity.getParserVersion(),
                entity.getPromptVersion(),
                deserialize(entity.getRequirementsJson(), JobRequirements.class, "requirementsJson"),
                entity.getConfirmedAt()
        );
    }

    /** 将能力差距快照转换为数据库Entity。 */
    public SkillGapSnapshotEntity toEntity(SkillGapSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");

        SkillGapSnapshotEntity entity = new SkillGapSnapshotEntity();
        entity.setSnapshotId(snapshot.snapshotId().toString());
        entity.setOwnerId(snapshot.ownerId().value());
        entity.setTargetRoleId(snapshot.targetRoleId().toString());
        entity.setTargetRoleVersion(snapshot.targetRoleVersion());
        entity.setProfileVersion(snapshot.profileVersion());
        entity.setAlgorithmVersion(snapshot.algorithmVersion());
        entity.setItemsJson(serialize(snapshot.items(), "gapItems"));
        entity.setCreatedAt(snapshot.createdAt());
        return entity;
    }

    /** 将数据库Entity还原为能力差距快照。 */
    public SkillGapSnapshot toDomain(SkillGapSnapshotEntity entity) {
        Objects.requireNonNull(entity, "entity 不能为空");

        return new SkillGapSnapshot(
                UUID.fromString(entity.getSnapshotId()),
                new ActorId(entity.getOwnerId()),
                UUID.fromString(entity.getTargetRoleId()),
                requireLong(entity.getTargetRoleVersion(), "targetRoleVersion"),
                requireLong(entity.getProfileVersion(), "profileVersion"),
                entity.getAlgorithmVersion(),
                deserialize(entity.getItemsJson(), GAP_ITEM_LIST_TYPE, "itemsJson"),
                entity.getCreatedAt()
        );
    }

    /** 将训练计划主记录转换为数据库Entity。 */
    public TrainingPlanEntity toEntity(TrainingPlan plan) {
        Objects.requireNonNull(plan, "plan 不能为空");

        TrainingPlanEntity entity = new TrainingPlanEntity();
        entity.setPlanId(plan.planId().toString());
        entity.setOwnerId(plan.ownerId().value());
        entity.setPlanVersion(plan.planVersion());
        entity.setGapSnapshotId(plan.gapSnapshotId().toString());
        entity.setTitle(plan.title());
        entity.setPlanStatus(plan.status().name());
        entity.setVersion(plan.version());
        entity.setCreatedAt(plan.createdAt());
        entity.setUpdatedAt(plan.updatedAt());
        entity.setActivatedAt(plan.activatedAt());
        entity.setCompletedAt(plan.completedAt());
        entity.setCancelledAt(plan.cancelledAt());
        return entity;
    }

    /**
     * 将计划项转换为数据库Entity。
     * planId和ownerId来自计划聚合，不能采用模型或客户端输入。
     */
    public TrainingPlanItemEntity toEntity(
            ActorId ownerId,
            UUID planId,
            TrainingPlanItem item
    ) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(planId, "planId 不能为空");
        Objects.requireNonNull(item, "item 不能为空");

        TrainingPlanItemEntity entity = new TrainingPlanItemEntity();
        entity.setItemId(item.itemId().toString());
        entity.setPlanId(planId.toString());
        entity.setOwnerId(ownerId.value());
        entity.setWeekNumber(item.weekNumber());
        entity.setTitle(item.title());
        entity.setTaskDescription(item.taskDescription());
        entity.setEstimatedMinutes(item.estimatedMinutes());
        entity.setCompletionCriteria(item.completionCriteria());
        entity.setEvidenceRequirement(item.evidenceRequirement());
        entity.setGapItemIdsJson(serialize(item.gapItemIds(), "gapItemIds"));
        entity.setFoundationGoal(item.foundationGoal());
        entity.setResourceRefsJson(serialize(item.resourceRefs(), "resourceRefs"));
        entity.setItemStatus(item.status().name());
        entity.setCompletionEvidenceRefsJson(
                serialize(item.completionEvidenceRefs(), "completionEvidenceRefs")
        );
        entity.setVersion(item.version());
        entity.setCreatedAt(item.createdAt());
        entity.setUpdatedAt(item.updatedAt());
        return entity;
    }

    /** 将目标岗位草案转换为数据库Entity。 */
    public TargetRoleDraftEntity toEntity(TargetRoleDraft draft) {
        Objects.requireNonNull(draft, "draft不能为空");

        TargetRoleDraftEntity entity = new TargetRoleDraftEntity();
        entity.setDraftId(draft.draftId().toString());
        entity.setOwnerId(draft.ownerId().value());
        entity.setSourceRef(draft.sourceRef());
        entity.setSourceHash(draft.sourceHash());
        entity.setParserVersion(draft.parserVersion());
        entity.setPromptVersion(draft.promptVersion());
        entity.setRequirementsJson(
                serialize(draft.requirementsSnapshot(), "requirements")
        );
        entity.setDraftStatus(draft.status().name());
        entity.setVersion(draft.version());
        entity.setCreatedAt(draft.createdAt());
        entity.setConfirmedTargetRoleId(
                draft.confirmedTargetRoleId() == null
                        ? null
                        : draft.confirmedTargetRoleId().toString()
        );
        entity.setConfirmedTargetRoleVersion(
                draft.confirmedTargetRoleVersion()
        );
        entity.setConfirmedAt(draft.confirmedAt());
        return entity;
    }

    /** 将数据库Entity还原为目标岗位草案。 */
    public TargetRoleDraft toDomain(TargetRoleDraftEntity entity) {
        Objects.requireNonNull(entity, "entity不能为空");

        return new TargetRoleDraft(
                UUID.fromString(entity.getDraftId()),
                new ActorId(entity.getOwnerId()),
                entity.getSourceRef(),
                entity.getSourceHash(),
                entity.getParserVersion(),
                entity.getPromptVersion(),
                deserialize(
                        entity.getRequirementsJson(),
                        JobRequirements.class,
                        "requirementsJson"
                ),
                TargetRoleDraft.Status.valueOf(entity.getDraftStatus()),
                requireLong(entity.getVersion(), "version"),
                entity.getCreatedAt(),
                entity.getConfirmedTargetRoleId() == null
                        ? null
                        : UUID.fromString(
                        entity.getConfirmedTargetRoleId()
                ),
                entity.getConfirmedTargetRoleVersion(),
                entity.getConfirmedAt()
        );
    }

    /**
     * 将计划主记录和计划项共同还原为完整训练计划聚合。
     * 每个计划项必须属于同一计划和同一owner。
     */
    public TrainingPlan toDomain(
            TrainingPlanEntity planEntity,
            List<TrainingPlanItemEntity> itemEntities
    ) {
        Objects.requireNonNull(planEntity, "planEntity 不能为空");

        if (itemEntities == null || itemEntities.isEmpty()) {
            throw new IllegalStateException("数据库训练计划必须包含计划项");
        }

        for (TrainingPlanItemEntity itemEntity : itemEntities) {
            if (itemEntity == null) {
                throw new IllegalStateException("数据库计划项不能包含空值");
            }
            if (!Objects.equals(planEntity.getPlanId(), itemEntity.getPlanId())
                    || !Objects.equals(planEntity.getOwnerId(), itemEntity.getOwnerId())) {
                throw new IllegalStateException("数据库计划项不属于当前训练计划");
            }
        }

        List<TrainingPlanItem> items = itemEntities.stream()
                .map(this::toDomain)
                .toList();

        return new TrainingPlan(
                UUID.fromString(planEntity.getPlanId()),
                new ActorId(planEntity.getOwnerId()),
                requireLong(planEntity.getPlanVersion(), "planVersion"),
                UUID.fromString(planEntity.getGapSnapshotId()),
                planEntity.getTitle(),
                TrainingPlan.PlanStatus.valueOf(planEntity.getPlanStatus()),
                items,
                requireLong(planEntity.getVersion(), "version"),
                planEntity.getCreatedAt(),
                planEntity.getUpdatedAt(),
                planEntity.getActivatedAt(),
                planEntity.getCompletedAt(),
                planEntity.getCancelledAt()
        );
    }

    /** 将数据库计划项Entity还原为领域对象。 */
    public TrainingPlanItem toDomain(TrainingPlanItemEntity entity) {
        Objects.requireNonNull(entity, "entity 不能为空");

        return new TrainingPlanItem(
                UUID.fromString(entity.getItemId()),
                requireInt(entity.getWeekNumber(), "weekNumber"),
                entity.getTitle(),
                entity.getTaskDescription(),
                requireInt(entity.getEstimatedMinutes(), "estimatedMinutes"),
                entity.getCompletionCriteria(),
                entity.getEvidenceRequirement(),
                deserialize(entity.getGapItemIdsJson(), UUID_LIST_TYPE, "gapItemIdsJson"),
                entity.getFoundationGoal(),
                deserialize(entity.getResourceRefsJson(), RESOURCE_REF_LIST_TYPE, "resourceRefsJson"),
                TrainingPlanItem.ItemStatus.valueOf(entity.getItemStatus()),
                deserialize(
                        entity.getCompletionEvidenceRefsJson(),
                        STRING_LIST_TYPE,
                        "completionEvidenceRefsJson"
                ),
                requireLong(entity.getVersion(), "version"),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String serialize(Object value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " 不能为空");

        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(fieldName + "序列化失败", exception);
        }
    }

    private <T> T deserialize(String json, Class<T> type, String fieldName) {
        requireJson(json, fieldName);

        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("数据库" + fieldName + "格式非法", exception);
        }
    }

    private <T> T deserialize(String json, TypeReference<T> type, String fieldName) {
        requireJson(json, fieldName);

        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("数据库" + fieldName + "格式非法", exception);
        }
    }

    private static void requireJson(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("数据库" + fieldName + "不能为空");
        }
    }

    private static long requireLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("数据库" + fieldName + "不能为空");
        }

        return value;
    }

    private static int requireInt(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("数据库" + fieldName + "不能为空");
        }

        return value;
    }
}