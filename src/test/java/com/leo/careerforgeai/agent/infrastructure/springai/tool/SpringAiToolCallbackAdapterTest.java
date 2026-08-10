package com.leo.careerforgeai.agent.infrastructure.springai.tool;

import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.agent.domain.tool.ToolRiskLevel;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.adapter.SpringAiToolCallbackAdapter;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.adapter.SpringAiToolDefinitionAdapter;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.lifecycle.SpringAiToolRunContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Spring AI ToolCallback复用公共契约、安全执行器和可信上下文。
 * @author: Miao Zheng
 * @date: 2026-08-07 20:00
 **/
@ExtendWith(MockitoExtension.class)
class SpringAiToolCallbackAdapterTest {

    @Mock
    private SafeToolExecutor safeToolExecutor;

    private ToolExecutionContext executionContext;
    private SpringAiToolRunContext runContext;
    private SpringAiToolCallbackAdapter callback;

    @BeforeEach
    void setUp() {
        ToolContract<TestInput, TestOutput> contract = new ToolContract<>(
                new ToolDefinition("search_career_materials", "搜索受控职业材料",
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}"),
                "{\"type\":\"object\"}",
                TestInput.class,
                TestOutput.class,
                ToolImplementationType.RETRIEVAL_BACKED,
                ToolRiskLevel.LOW,
                true,
                1_000,
                10_000,
                10,
                Duration.ofSeconds(10)
        );
        executionContext = new ToolExecutionContext(
                "run-1",
                Instant.parse("2026-08-07T12:00:00Z"),
                new RetrievalScope("knowledge-base-1", Set.of(), Set.of())
        );
        runContext = new SpringAiToolRunContext(executionContext);
        callback = new SpringAiToolCallbackAdapter(
                contract, new SpringAiToolDefinitionAdapter(), safeToolExecutor);
    }

    @Test
    void shouldExecuteThroughExistingSafeToolExecutor() {
        String resultJson = "{\"status\":\"SUCCESS\",\"data\":{},\"error\":null}";
        ToolExecutionResult executionResult = ToolExecutionResult.success(
                "spring-ai-local-1", "search_career_materials", resultJson, 1, null, null);
        when(safeToolExecutor.execute(any(ToolCall.class), same(executionContext)))
                .thenReturn(executionResult);

        String result = callback.call(
                "{\"query\":\"Java并发\"}",
                new ToolContext(runContext.asToolContextMap())
        );

        assertThat(result).isEqualTo(resultJson);
        assertThat(runContext.results()).containsExactly(executionResult);

        ArgumentCaptor<ToolCall> captor = ArgumentCaptor.forClass(ToolCall.class);
        verify(safeToolExecutor).execute(captor.capture(), same(executionContext));
        assertThat(captor.getValue().id()).isEqualTo("spring-ai-local-1");
        assertThat(captor.getValue().name()).isEqualTo("search_career_materials");
        assertThat(captor.getValue().argumentsJson()).isEqualTo("{\"query\":\"Java并发\"}");
    }

    @Test
    void shouldFailClosedWithoutTrustedContext() {
        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Spring AI工具调用缺少服务端运行上下文");

        verifyNoInteractions(safeToolExecutor);
    }

    @Test
    void shouldMapProtocolInvalidArgumentsWithoutExecutingTool() {
        String result = callback.call(
                "a".repeat(30_001),
                new ToolContext(runContext.asToolContextMap())
        );

        assertThat(result).contains("\"status\":\"FAILURE\"")
                .contains("\"type\":\"INVALID_ARGUMENTS\"");
        assertThat(runContext.results()).singleElement()
                .satisfies(toolResult -> {
                    assertThat(toolResult.errorType()).isEqualTo(ToolExecutionErrorType.INVALID_ARGUMENTS);
                    assertThat(toolResult.toolCallId()).isEqualTo("spring-ai-local-1");
                });
        verifyNoInteractions(safeToolExecutor);
    }

    private record TestInput(String query) {
    }

    private record TestOutput(String status) {
    }
}