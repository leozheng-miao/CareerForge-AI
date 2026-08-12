package com.leo.careerforgeai.memory.domain.profile;

/**
 * @program: CareerForge-AI
 * @description: 定义长期记忆候选从待确认到失效归档的生命周期状态
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public enum MemoryStatus {

    PENDING,
    CONFIRMED,
    REJECTED,
    SUPERSEDED,
    REVOKED;

    public boolean canTransitionTo(MemoryStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }

        return switch (this) {
            case PENDING ->
                    targetStatus == CONFIRMED
                            || targetStatus == REJECTED;
            case CONFIRMED ->
                    targetStatus == SUPERSEDED
                            || targetStatus == REVOKED;
            case REJECTED, SUPERSEDED, REVOKED -> false;
        };
    }

    public boolean isEffectiveProfileMemory() {
        return this == CONFIRMED;
    }

    public boolean isTerminal() {
        return this == REJECTED
                || this == SUPERSEDED
                || this == REVOKED;
    }
}