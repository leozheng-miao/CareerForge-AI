package com.leo.careerforgeai.career.application.port;

import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TargetRoleDraft;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义目标岗位、能力差距和训练计划的持久化端口并统一强制owner访问边界
 * @author: Miao Zheng
 * @date: 2026-08-12
 */
public interface CareerPlanningRepository {
    /** 保存用户确认后创建的不可变目标岗位版本。 */
    void insertTargetRole(TargetRole targetRole);

    /** 使用ownerId和targetRoleId共同查询目标岗位。 */
    Optional<TargetRole> findTargetRole(ActorId ownerId, UUID targetRoleId);

    /** 查询当前用户业务版本最高的目标岗位。 */
    Optional<TargetRole> findLatestTargetRole(ActorId ownerId);

    /** 保存基于固定目标岗位和画像版本生成的不可变差距快照。 */
    void insertSkillGapSnapshot(SkillGapSnapshot snapshot);

    /** 使用ownerId和snapshotId共同查询能力差距快照。 */
    Optional<SkillGapSnapshot> findSkillGapSnapshot(ActorId ownerId, UUID snapshotId);

    /** 查询相同岗位、画像和算法版本已经生成的差距快照。 */
    Optional<SkillGapSnapshot> findSkillGapSnapshotByInputVersions(
            ActorId ownerId,
            UUID targetRoleId,
            long targetRoleVersion,
            long profileVersion,
            String algorithmVersion
    );

    /** 保存训练计划及其全部计划项。 */
    void insertTrainingPlan(TrainingPlan trainingPlan);

    /** 使用ownerId和planId共同查询完整训练计划。 */
    Optional<TrainingPlan> findTrainingPlan(ActorId ownerId, UUID planId);

    /** 查询当前用户业务版本最高的训练计划及其全部计划项。 */
    Optional<TrainingPlan> findLatestTrainingPlan(ActorId ownerId);

    /** 使用owner和旧version更新计划状态及计划项进度。 */
    boolean updateTrainingPlanIfVersionMatches(
            ActorId ownerId,
            TrainingPlan updatedPlan,
            long expectedVersion
    );

    /** 保存尚未确认的目标岗位草案。 */
    void insertTargetRoleDraft(TargetRoleDraft draft);

    /** 使用当前owner和draftId共同查询目标岗位草案。 */
    Optional<TargetRoleDraft> findTargetRoleDraft(
            ActorId ownerId,
            UUID draftId
    );

    /** 原子创建不可变TargetRole并确认来源草案。 */
    void confirmTargetRoleDraft(
            ActorId ownerId,
            TargetRoleDraft confirmedDraft,
            TargetRole targetRole,
            long expectedDraftVersion
    );
}