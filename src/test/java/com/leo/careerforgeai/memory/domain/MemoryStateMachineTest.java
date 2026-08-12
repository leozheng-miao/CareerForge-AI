package com.leo.careerforgeai.memory.domain;

import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStateMachine;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryTransitionException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Memory合法状态转换、重复决策拒绝和长期画像生效边界
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class MemoryStateMachineTest {

    @Test
    void shouldAllowOnlyExplicitBusinessTransitions() {
        assertThat(MemoryStateMachine.transition(
                MemoryStatus.PENDING,
                MemoryDecisionType.CONFIRM
        )).isEqualTo(MemoryStatus.CONFIRMED);

        assertThat(MemoryStateMachine.transition(
                MemoryStatus.PENDING,
                MemoryDecisionType.REJECT
        )).isEqualTo(MemoryStatus.REJECTED);

        assertThat(MemoryStateMachine.transition(
                MemoryStatus.CONFIRMED,
                MemoryDecisionType.SUPERSEDE
        )).isEqualTo(MemoryStatus.SUPERSEDED);

        assertThat(MemoryStateMachine.transition(
                MemoryStatus.CONFIRMED,
                MemoryDecisionType.REVOKE
        )).isEqualTo(MemoryStatus.REVOKED);
    }

    @Test
    void shouldRejectRepeatedOrIllegalDecisions() {
        assertThatThrownBy(() -> MemoryStateMachine.transition(
                MemoryStatus.CONFIRMED,
                MemoryDecisionType.CONFIRM
        ))
                .isInstanceOf(MemoryTransitionException.class)
                .hasMessageContaining("CONFIRMED")
                .hasMessageContaining("CONFIRM");

        assertThatThrownBy(() -> MemoryStateMachine.transition(
                MemoryStatus.PENDING,
                MemoryDecisionType.REVOKE
        ))
                .isInstanceOf(MemoryTransitionException.class);

        for (MemoryStatus terminalStatus : Arrays.asList(
                MemoryStatus.REJECTED,
                MemoryStatus.SUPERSEDED,
                MemoryStatus.REVOKED
        )) {
            assertThatThrownBy(() -> MemoryStateMachine.transition(
                    terminalStatus,
                    MemoryDecisionType.CONFIRM
            )).isInstanceOf(MemoryTransitionException.class);
        }
    }

    @Test
    void shouldTreatOnlyConfirmedMemoryAsEffectiveProfileData() {
        assertThat(MemoryStatus.CONFIRMED.isEffectiveProfileMemory()).isTrue();

        assertThat(Arrays.stream(MemoryStatus.values())
                .filter(status -> status != MemoryStatus.CONFIRMED)
                .noneMatch(MemoryStatus::isEffectiveProfileMemory))
                .isTrue();
    }
}