package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.agent.domain.loop.AgentInputEstimate;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.loop.AgentModelCallTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentModelOutcome;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.loop.AgentToolCallTrace;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.AssistantToolCallsMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import com.leo.careerforgeai.model.domain.toolcalling.ToolResultMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证 Agent Loop 策略、指纹、估算、状态限制和 Trace 累计。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:53
 **/
class AgentLoopFoundationTest {

    private static final Instant NOW = Instant.parse("2026-08-06T08:00:00Z");

    private final ToolCallFingerprintService fingerprintService =
            new ToolCallFingerprintService(JsonMapper.builder().build());

    @Test
    @DisplayName("规范化参数并忽略Tool Call ID生成重复指纹")
    void shouldCreateCanonicalToolCallFingerprint() {
        ToolCall first = new ToolCall("call-1", "search_tool", "{\"query\":\"Java\",\"topK\":5}");
        ToolCall reordered = new ToolCall("call-2", "search_tool", "{ \"topK\": 5, \"query\": \"Java\" }");
        ToolCall changedValue = new ToolCall("call-3", "search_tool", "{\"query\":\"Java\",\"topK\":6}");

        String firstFingerprint = fingerprintService.fingerprint(first, "scope-v1");

        assertThat(fingerprintService.fingerprint(reordered, "scope-v1")).isEqualTo(firstFingerprint);
        assertThat(fingerprintService.fingerprint(changedValue, "scope-v1")).isNotEqualTo(firstFingerprint);
        assertThat(fingerprintService.fingerprint(first, "scope-v2")).isNotEqualTo(firstFingerprint);
        assertThat(firstFingerprint).hasSize(64);

        ToolCall invalidOne = new ToolCall("call-4", "search_tool", "{invalid");
        ToolCall invalidTwo = new ToolCall("call-5", "search_tool", "  {invalid  ");
        assertThat(fingerprintService.fingerprint(invalidOne, "scope-v1"))
                .isEqualTo(fingerprintService.fingerprint(invalidTwo, "scope-v1"));
    }

    @Test
    @DisplayName("估算消息、工具定义、Tool Calls和Tool Results的模型输入")
    void shouldEstimateCompleteToolCallingInput() {
        HeuristicAgentTokenEstimator estimator = new HeuristicAgentTokenEstimator();
        ToolDefinition definition = definition();

        List<ToolCallingMessage> initialMessages = List.of(
                new ToolCallingTextMessage(ModelRole.SYSTEM, "你是职业辅导助手"),
                new ToolCallingTextMessage(ModelRole.USER, "查找 Java 并发面经")
        );

        AgentInputEstimate initial = estimator.estimate(initialMessages, List.of(definition));

        ToolCall toolCall = new ToolCall("call-1", "search_tool", "{\"query\":\"Java并发\"}");
        List<ToolCallingMessage> expandedMessages = List.of(
                initialMessages.get(0),
                initialMessages.get(1),
                new AssistantToolCallsMessage(List.of(toolCall)),
                new ToolResultMessage("call-1", "search_tool",
                        "{\"status\":\"SUCCESS\",\"data\":{\"items\":[\"chunk-1\"]}}")
        );

        AgentInputEstimate expanded = estimator.estimate(expandedMessages, List.of(definition));

        assertThat(initial.estimatedInputTokens()).isPositive();
        assertThat(initial.messageHistoryChars()).isPositive();
        assertThat(expanded.estimatedInputTokens()).isGreaterThan(initial.estimatedInputTokens());
        assertThat(expanded.messageHistoryChars()).isGreaterThan(initial.messageHistoryChars());
    }

    @Test
    @DisplayName("模型调用前检查Deadline、轮数、消息和Token预算")
    void shouldApplyModelCallPreflightLimits() {
        AgentRunState normalState = state(defaultPolicy());

        assertThat(normalState.checkBeforeModelCall(100, 200, NOW)).isEmpty();
        assertThat(normalState.modelCallTimeout(NOW)).isEqualTo(Duration.ofSeconds(10));
        assertThat(normalState.modelCallTimeout(NOW.plusSeconds(25))).isEqualTo(Duration.ofSeconds(5));
        assertThat(normalState.checkBeforeModelCall(100, 200, NOW.plusSeconds(30)))
                .contains(AgentTerminationReason.AGENT_DEADLINE_EXCEEDED);

        AgentRunState iterationState = state(defaultPolicy());
        iterationState.startModelIteration();
        iterationState.startModelIteration();
        assertThat(iterationState.checkBeforeModelCall(100, 200, NOW))
                .contains(AgentTerminationReason.MAX_MODEL_ITERATIONS);

        assertThat(state(defaultPolicy()).checkBeforeModelCall(100, 501, NOW))
                .contains(AgentTerminationReason.MESSAGE_HISTORY_LIMIT_EXCEEDED);
        assertThat(state(defaultPolicy()).checkBeforeModelCall(901, 200, NOW))
                .contains(AgentTerminationReason.TOKEN_BUDGET_EXCEEDED);
    }

    @Test
    @DisplayName("整轮原子登记工具次数并限制重复、单工具和总调用数")
    void shouldAtomicallyRegisterToolCalls() {
        AgentRunState repeatedState = state(policy(3, 3, 2));
        repeatedState.startModelIteration();

        ToolCall first = new ToolCall("call-1", "search_tool", "{\"query\":\"Java\",\"topK\":5}");
        ToolCall second = new ToolCall("call-2", "search_tool", "{\"topK\":5,\"query\":\"Java\"}");
        ToolCall third = new ToolCall("call-3", "search_tool", "{\"query\":\"Java\",\"topK\":5}");

        assertThat(repeatedState.registerToolCalls(
                List.of(first, second), fingerprintService, "scope-v1")).isEmpty();
        assertThat(repeatedState.totalToolCalls()).isEqualTo(2);

        assertThat(repeatedState.registerToolCalls(
                List.of(third), fingerprintService, "scope-v1"))
                .contains(AgentTerminationReason.REPEATED_TOOL_CALL);
        assertThat(repeatedState.totalToolCalls()).isEqualTo(2);

        ToolCall different = new ToolCall("call-4", "search_tool", "{\"query\":\"JVM\"}");
        assertThat(repeatedState.registerToolCalls(
                List.of(different), fingerprintService, "scope-v1")).isEmpty();
        assertThat(repeatedState.totalToolCalls()).isEqualTo(3);

        assertThat(repeatedState.registerToolCalls(
                List.of(new ToolCall("call-5", "other_tool", "{}")), fingerprintService, "scope-v1"))
                .contains(AgentTerminationReason.MAX_TOTAL_TOOL_CALLS);
        assertThat(repeatedState.totalToolCalls()).isEqualTo(3);

        AgentRunState perToolState = state(policy(5, 2, 2));
        perToolState.startModelIteration();
        assertThat(perToolState.registerToolCalls(List.of(
                new ToolCall("call-1", "search_tool", "{\"query\":\"A\"}"),
                new ToolCall("call-2", "search_tool", "{\"query\":\"B\"}")
        ), fingerprintService, "scope-v1")).isEmpty();

        assertThat(perToolState.registerToolCalls(
                List.of(new ToolCall("call-3", "search_tool", "{\"query\":\"C\"}")),
                fingerprintService,
                "scope-v1"
        )).contains(AgentTerminationReason.MAX_CALLS_PER_TOOL);
        assertThat(perToolState.totalToolCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("累计外层模型和MODEL_BACKED工具Token并生成不可变Trace")
    void shouldAccumulateUsageAndCreateTraceSnapshot() {
        AgentRunState state = state(defaultPolicy());

        int firstIteration = state.startModelIteration();
        state.recordModelCall(new AgentModelCallTrace(
                firstIteration,
                "request-1",
                "deepseek-v4-flash",
                AgentModelOutcome.TOOL_CALLS,
                20,
                80,
                new ModelUsage(100, 20, 120),
                null
        ));
        state.recordToolCall(new AgentToolCallTrace(
                firstIteration,
                1,
                "call-1",
                "parse_job_requirements",
                ToolImplementationType.MODEL_BACKED,
                ToolExecutionStatus.SUCCESS,
                30,
                100,
                200,
                1,
                null,
                new ModelUsage(30, 10, 40),
                25L
        ));

        int secondIteration = state.startModelIteration();
        state.recordModelCall(new AgentModelCallTrace(
                secondIteration,
                "request-2",
                "deepseek-v4-flash",
                AgentModelOutcome.FINAL_ANSWER,
                15,
                150,
                new ModelUsage(50, 10, 60),
                null
        ));

        assertThat(state.totalUsage()).isEqualTo(new ModelUsage(180, 40, 220));

        AgentRunTrace trace = state.snapshot(
                AgentRunStatus.COMPLETED,
                AgentTerminationReason.FINAL_ANSWER,
                NOW.plusSeconds(2)
        );

        assertThat(trace.modelCalls()).hasSize(2);
        assertThat(trace.toolCalls()).hasSize(1);
        assertThat(trace.toolCalls().getFirst().modelDurationMs()).isEqualTo(25L);
        assertThat(trace.totalUsage()).isEqualTo(new ModelUsage(180, 40, 220));
        assertThat(trace.durationMs()).isEqualTo(2000);
        assertThatThrownBy(() -> trace.modelCalls().add(trace.modelCalls().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("拒绝互相矛盾的Loop限制")
    void shouldRejectInvalidLoopPolicy() {
        assertThatThrownBy(() -> new AgentLoopPolicy(
                2, 4, 3, 1, 1000, 100, 500,
                Duration.ofSeconds(30), Duration.ofSeconds(10)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRepeatedCallCount");

        assertThatThrownBy(() -> new AgentLoopPolicy(
                2, 4, 3, 2, 1000, 100, 500,
                Duration.ofSeconds(10), Duration.ofSeconds(20)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelCallTimeout");
    }

    /** 创建测试使用的 Agent Run 状态。 */
    private AgentRunState state(AgentLoopPolicy policy) {
        return new AgentRunState("run-1", policy, NOW);
    }

    /** 创建常规测试策略。 */
    private AgentLoopPolicy defaultPolicy() {
        return policy(4, 3, 2);
    }

    /** 创建具有指定工具限制的测试策略。 */
    private AgentLoopPolicy policy(
            int maxTotalToolCalls,
            int maxCallsPerTool,
            int maxRepeatedCallCount
    ) {
        return new AgentLoopPolicy(
                2,
                maxTotalToolCalls,
                maxCallsPerTool,
                maxRepeatedCallCount,
                1000,
                100,
                500,
                Duration.ofSeconds(30),
                Duration.ofSeconds(10)
        );
    }

    /** 创建测试使用的公共工具定义。 */
    private ToolDefinition definition() {
        return new ToolDefinition(
                "search_tool",
                "检索职业材料",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}"
        );
    }
}