package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.MockInterviewInputSnapshot;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义模拟面试冻结输入快照的幂等保存和owner隔离查询边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public interface MockInterviewInputSnapshotRepository {

    MockInterviewInputSnapshot claim(MockInterviewInputSnapshot candidate);

    Optional<MockInterviewInputSnapshot> findById(ActorId ownerId, UUID inputSnapshotId);

    Optional<MockInterviewInputSnapshot> findByHash(ActorId ownerId, String snapshotHash);
}