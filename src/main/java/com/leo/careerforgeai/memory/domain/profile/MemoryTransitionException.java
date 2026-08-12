package com.leo.careerforgeai.memory.domain.profile;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示Memory状态与用户决策不符合受控状态机规则
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public final class MemoryTransitionException extends RuntimeException {

    private final MemoryStatus currentStatus;
    private final MemoryDecisionType decisionType;

    public MemoryTransitionException(
            MemoryStatus currentStatus,
            MemoryDecisionType decisionType
    ) {
        super("Memory状态不允许执行当前决策: status="
                + Objects.requireNonNull(currentStatus, "currentStatus 不能为空")
                + ", decision="
                + Objects.requireNonNull(decisionType, "decisionType 不能为空"));
        this.currentStatus = currentStatus;
        this.decisionType = decisionType;
    }

    public MemoryStatus currentStatus() {
        return currentStatus;
    }

    public MemoryDecisionType decisionType() {
        return decisionType;
    }
}