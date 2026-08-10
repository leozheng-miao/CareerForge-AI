package com.leo.careerforgeai.agent.infrastructure.springai.tool;

import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Spring AI请求级可信工具上下文和结果收集边界。
 * @author: Miao Zheng
 * @date: 2026-08-07 19:50
 **/
class SpringAiToolRunContextTest {

    @Test
    void shouldCarryTrustedContextAndCollectResults() {
        ToolExecutionContext executionContext = new ToolExecutionContext(
                "run-1",
                Instant.parse("2026-08-07T12:00:00Z"),
                new RetrievalScope("knowledge-base-1", Set.of(), Set.of())
        );
        SpringAiToolRunContext runContext = new SpringAiToolRunContext(executionContext);
        ToolContext toolContext = new ToolContext(runContext.asToolContextMap());

        assertThat(SpringAiToolRunContext.requireFrom(toolContext)).isSameAs(runContext);
        assertThat(runContext.executionContext()).isSameAs(executionContext);
        assertThat(runContext.nextLocalToolCallId()).isEqualTo("spring-ai-local-1");
        assertThat(runContext.nextLocalToolCallId()).isEqualTo("spring-ai-local-2");

        ToolExecutionResult result = ToolExecutionResult.success(
                "spring-ai-local-1", "search_career_materials", "{}", 0, null, null);
        runContext.record(result);

        assertThat(runContext.results()).containsExactly(result);
        assertThatThrownBy(() -> runContext.results().add(result))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectMissingTrustedRunContext() {
        assertThatThrownBy(() -> SpringAiToolRunContext.requireFrom(new ToolContext(java.util.Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Spring AI工具运行上下文缺失");
    }

    @Test
    void shouldEnforceModelIterationLimit() {
        SpringAiToolRunContext runContext = new SpringAiToolRunContext(
                new ToolExecutionContext(
                        "run-1",
                        Instant.parse("2026-08-07T12:01:00Z"),
                        new RetrievalScope("knowledge-base-1", Set.of(), Set.of())
                )
        );
        Instant now = Instant.parse("2026-08-07T12:00:00Z");

        assertThat(runContext.startModelIteration(2, now)).isEqualTo(1);
        assertThat(runContext.startModelIteration(2, now)).isEqualTo(2);

        assertThatThrownBy(() -> runContext.startModelIteration(2, now))
                .isInstanceOfSatisfying(
                        SpringAiToolLoopLimitException.class,
                        exception -> assertThat(exception.getLimitType())
                                .isEqualTo(SpringAiToolLoopLimitType.MAX_MODEL_ITERATIONS)
                );
        assertThat(runContext.modelIterations()).isEqualTo(2);
    }

    @Test
    void shouldRejectModelCallAtDeadline() {
        Instant deadline = Instant.parse("2026-08-07T12:00:00Z");
        SpringAiToolRunContext runContext = new SpringAiToolRunContext(
                new ToolExecutionContext(
                        "run-1",
                        deadline,
                        new RetrievalScope("knowledge-base-1", Set.of(), Set.of())
                )
        );

        assertThatThrownBy(() -> runContext.startModelIteration(2, deadline))
                .isInstanceOfSatisfying(
                        SpringAiToolLoopLimitException.class,
                        exception -> assertThat(exception.getLimitType())
                                .isEqualTo(SpringAiToolLoopLimitType.DEADLINE_EXCEEDED)
                );
        assertThat(runContext.modelIterations()).isZero();
    }
}