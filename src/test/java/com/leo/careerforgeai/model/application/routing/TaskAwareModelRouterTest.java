package com.leo.careerforgeai.model.application.routing;

import com.leo.careerforgeai.model.domain.routing.ModelCapability;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ModelRouteDecision;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.routing.ReasoningMode;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证任务路由的能力筛选、预算、Deadline、Fallback和配置隔离。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
class TaskAwareModelRouterTest {

    @Test
    void shouldSelectCompatibleProfilesWithoutRetainingMutableConfiguration() {
        ModelExecutionProfile disabled = profile("disabled", Set.of(ModelCapability.CHAT,
                ModelCapability.JSON_SCHEMA), 2_000, Duration.ofSeconds(30), false);
        ModelExecutionProfile incapable = profile("incapable", Set.of(ModelCapability.CHAT),
                2_000, Duration.ofSeconds(30), true);
        ModelExecutionProfile primary = profile("primary", Set.of(ModelCapability.CHAT,
                ModelCapability.JSON_SCHEMA), 2_000, Duration.ofSeconds(30), true);
        ModelExecutionProfile fallback = profile("fallback", Set.of(ModelCapability.CHAT,
                ModelCapability.JSON_SCHEMA), 2_000, Duration.ofSeconds(20), true);
        List<ModelExecutionProfile> configured = new ArrayList<>(
                List.of(disabled, incapable, primary, fallback));
        TaskAwareModelRouter router = new TaskAwareModelRouter(
                Map.of(ModelTaskType.CAREER_COACH, configured), "routing-v1");
        configured.clear();

        ModelRouteDecision decision = router.route(ModelTaskType.CAREER_COACH,
                Set.of(ModelCapability.CHAT, ModelCapability.JSON_SCHEMA),
                1_000, 2_000, Duration.ofSeconds(60), true);

        assertThat(decision.selectedProfile()).isEqualTo(primary);
        assertThat(decision.fallbackProfiles()).containsExactly(fallback);
        assertThat(decision.reasonCode()).isEqualTo("CONSTRAINT_FALLBACK");
        assertThat(decision.routingVersion()).isEqualTo("routing-v1");
    }

    @Test
    void shouldSuppressFallbackWhenBusinessPolicyDisallowsIt() {
        ModelExecutionProfile primary = profile("primary", Set.of(ModelCapability.CHAT),
                2_000, Duration.ofSeconds(30), true);
        ModelExecutionProfile fallback = profile("fallback", Set.of(ModelCapability.CHAT),
                2_000, Duration.ofSeconds(20), true);
        TaskAwareModelRouter router = new TaskAwareModelRouter(
                Map.of(ModelTaskType.RAG_ANSWER, List.of(primary, fallback)), "routing-v1");

        ModelRouteDecision decision = router.route(ModelTaskType.RAG_ANSWER,
                Set.of(ModelCapability.CHAT), 500, 1_000, Duration.ofSeconds(60), false);

        assertThat(decision.selectedProfile()).isEqualTo(primary);
        assertThat(decision.fallbackProfiles()).isEmpty();
        assertThat(decision.reasonCode()).isEqualTo("TASK_DEFAULT");
    }

    @Test
    void shouldRejectInsufficientBudgetAndMissingCompatibleProfile() {
        TaskAwareModelRouter router = new TaskAwareModelRouter(
                Map.of(ModelTaskType.CAREER_COACH, List.of(profile("chat-only",
                        Set.of(ModelCapability.CHAT), 2_000, Duration.ofSeconds(30), true))),
                "routing-v1");

        assertThatThrownBy(() -> router.route(ModelTaskType.CAREER_COACH,
                Set.of(ModelCapability.CHAT), 1_001, 1_000, Duration.ofSeconds(60), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("剩余Token预算不足");

        assertThatThrownBy(() -> router.route(ModelTaskType.CAREER_COACH,
                Set.of(ModelCapability.JSON_SCHEMA), 500, 1_000, Duration.ofSeconds(60), true))
                .isInstanceOfSatisfying(ModelException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ModelErrorType.CONFIGURATION_ERROR));

        assertThatThrownBy(() -> router.route(ModelTaskType.CAREER_COACH,
                Set.of(ModelCapability.CHAT), 500, 1_000, Duration.ZERO, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("remainingDeadline必须大于0");
    }

    private static ModelExecutionProfile profile(String id, Set<ModelCapability> capabilities,
                                                 int maxOutputTokens, Duration timeout, boolean enabled) {
        return new ModelExecutionProfile(id, "deepseek", "deepseek-v4-flash", capabilities,
                ReasoningMode.DISABLED, null, maxOutputTokens, timeout,
                "deepseek-2026-09-01", enabled);
    }
}