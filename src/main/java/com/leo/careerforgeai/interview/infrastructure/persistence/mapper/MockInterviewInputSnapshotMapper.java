package com.leo.careerforgeai.interview.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.MockInterviewInputSnapshotEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @program: CareerForge-AI
 * @description: 操作模拟面试冻结输入快照主表
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Mapper
public interface MockInterviewInputSnapshotMapper extends BaseMapper<MockInterviewInputSnapshotEntity> {

    @Insert("""
            INSERT INTO mock_interview_input_snapshot (
                input_snapshot_id, owner_id, schema_version,
                target_role_id, target_role_version, skill_gap_snapshot_id,
                training_plan_id, training_plan_version,
                snapshot_context_json, snapshot_hash, created_at
            )
            VALUES (
                #{snapshot.inputSnapshotId}, #{snapshot.ownerId}, #{snapshot.schemaVersion},
                #{snapshot.targetRoleId}, #{snapshot.targetRoleVersion}, #{snapshot.skillGapSnapshotId},
                #{snapshot.trainingPlanId}, #{snapshot.trainingPlanVersion},
                #{snapshot.snapshotContextJson}, #{snapshot.snapshotHash}, #{snapshot.createdAt}
            )
            ON DUPLICATE KEY UPDATE input_snapshot_id = input_snapshot_id
            """)
    int claim(@Param("snapshot") MockInterviewInputSnapshotEntity snapshot);
}