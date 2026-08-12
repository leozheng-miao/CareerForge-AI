package com.leo.careerforgeai.memory.domain;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 以确定性规则执行Memory状态转换并拒绝非法或重复决策
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public final class MemoryStateMachine {

    private MemoryStateMachine() {
    }

    public static MemoryStatus transition(
            MemoryStatus currentStatus,
            MemoryDecisionType decisionType
    ) {
        Objects.requireNonNull(currentStatus, "currentStatus 不能为空");
        Objects.requireNonNull(decisionType, "decisionType 不能为空");

        MemoryStatus targetStatus = decisionType.targetStatus();

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new MemoryTransitionException(currentStatus, decisionType);
        }

        return targetStatus;
    }
}