package com.leo.careerforgeai.memory.domain;

/**
 * @program: CareerForge-AI
 * @description: 定义用户能够对Memory候选或已确认Memory执行的显式决策
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public enum MemoryDecisionType {

    CONFIRM(MemoryStatus.CONFIRMED),
    REJECT(MemoryStatus.REJECTED),
    SUPERSEDE(MemoryStatus.SUPERSEDED),
    REVOKE(MemoryStatus.REVOKED);

    private final MemoryStatus targetStatus;

    MemoryDecisionType(MemoryStatus targetStatus) {
        this.targetStatus = targetStatus;
    }

    public MemoryStatus targetStatus() {
        return targetStatus;
    }
}