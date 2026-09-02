package com.leo.careerforgeai.model.domain.routing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证模型执行Profile和路由决策的能力、Thinking及不可变边界。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
class ModelRoutingContractsTest {

    @Test
    void shouldCreateImmutableProfileAndRouteDecision() {
        Set<ModelCapability> capabilities = new java.util.HashSet<>(
                Set.of(ModelCapability.CHAT, ModelCapability.JSON_SCHEMA, ModelCapability.THINKING)
        );
        ModelExecutionProfile primary = profile(
                "career-coach-standard", capabilities,
                ReasoningMode.ADAPTIVE, ReasoningEffort.MEDIUM
        );
        ModelExecutionProfile fallback = profile(
                "career-coach-fallback",
                Set.of(ModelCapability.CHAT, ModelCapability.JSON_SCHEMA),
                ReasoningMode.DISABLED, null
        );
        List<ModelExecutionProfile> fallbacks = new ArrayList<>(List.of(fallback));

        ModelRouteDecision decision = new ModelRouteDecision(
                ModelTaskType.CAREER_COACH,
                primary,
                fallbacks,
                "routing-v1",
                "TASK_DEFAULT"
        );
        capabilities.clear();
        fallbacks.clear();

        assertThat(primary.capabilities()).containsExactlyInAnyOrder(
                ModelCapability.CHAT, ModelCapability.JSON_SCHEMA, ModelCapability.THINKING
        );
        assertThat(decision.fallbackProfiles()).containsExactly(fallback);
        assertThatThrownBy(() -> decision.fallbackProfiles().add(primary))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidReasoningAndDuplicateFallback() {
        assertThatThrownBy(() -> profile(
                "invalid-thinking",
                Set.of(ModelCapability.CHAT),
                ReasoningMode.ENABLED,
                ReasoningEffort.HIGH
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持Thinking");

        ModelExecutionProfile primary = profile(
                "primary",
                Set.of(ModelCapability.CHAT),
                ReasoningMode.DISABLED,
                null
        );
        assertThatThrownBy(() -> new ModelRouteDecision(
                ModelTaskType.CAREER_COACH,
                primary,
                List.of(primary),
                "routing-v1",
                "TASK_DEFAULT"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复Profile");
    }

    private static ModelExecutionProfile profile(
            String profileId,
            Set<ModelCapability> capabilities,
            ReasoningMode reasoningMode,
            ReasoningEffort effort
    ) {
        return new ModelExecutionProfile(
                profileId,
                "deepseek",
                "deepseek-v4-flash",
                capabilities,
                reasoningMode,
                effort,
                2_000,
                Duration.ofSeconds(30),
                "deepseek-2026-09-01",
                true
        );
    }
}