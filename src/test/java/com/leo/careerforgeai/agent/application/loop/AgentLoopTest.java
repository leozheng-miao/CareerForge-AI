package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.agent.application.tool.AgentTool;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolExecutionException;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.domain.loop.AgentInputEstimate;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopRequest;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.AgentModelOutcome;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.tool.AgentToolOutput;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.agent.domain.tool.ToolRiskLevel;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.AssistantToolCallsMessage;
import com.leo.careerforgeai.model.domain.toolcalling.FinalAnswerResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallsResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import com.leo.careerforgeai.model.domain.toolcalling.ToolResultMessage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leo.careerforgeai.agent.domain.loop.AgentInputEstimate;
import com.leo.careerforgeai.agent.domain.loop.AgentModelOutcome;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;

import java.time.ZoneId;

import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @program: CareerForge-AI
 * @description: 验证 Agent Loop 的直接回答、工具回放、串行执行和部分失败闭环。
 * @author: Miao Zheng
 * @date: 2026-08-06 18:30
 **/
class AgentLoopTest {

    private static final Instant NOW = Instant.parse("2026-08-06T08:00:00Z");
    private static final String INPUT_SCHEMA = """
            {
              "type":"object",
              "properties":{"query":{"type":"string"}},
              "required":["query"],
              "additionalProperties":false
            }
            """;
    private static final String OUTPUT_SCHEMA = "{\"type\":\"string\"}";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @AfterEach
    void closeResources() {
        executorService.shutdownNow();
        validatorFactory.close();
    }

    @Test
    @DisplayName("模型直接回答时不执行工具并返回完成状态")
    void shouldReturnDirectFinalAnswer() {
        AtomicInteger executions = new AtomicInteger();
        TestTool unusedTool = tool("search_tool", input -> {
            executions.incrementAndGet();
            return "unused";
        });
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class))).thenReturn(finalAnswer("request-1", "直接回答"));

        AgentLoopResult result = loop(gateway, List.of(unusedTool)).run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.FINAL_ANSWER);
        assertThat(result.finalContent()).isEqualTo("直接回答");
        assertThat(result.trace().modelCalls()).hasSize(1);
        assertThat(result.trace().toolCalls()).isEmpty();
        assertThat(executions).hasValue(0);
        assertThat(result.toolResults()).isEmpty();
        verify(gateway).call(any(ToolCallingRequest.class));
    }

    @Test
    @DisplayName("执行一次工具后将关联结果回传模型并获得最终回答")
    void shouldReplayToolResultAndReturnFinalAnswer() {
        List<String> executions = new ArrayList<>();
        TestTool searchTool = tool("search_tool", input -> {
            executions.add(input.query());
            return "evidence-" + input.query();
        });
        ToolCall toolCall = new ToolCall("call-1", "search_tool", "{\"query\":\"Java并发\"}");
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class))).thenReturn(
                new ToolCallsResult("request-1", "deepseek-v4-flash", List.of(toolCall), new ModelUsage(50, 10, 60)),
                finalAnswer("request-2", "基于证据的回答")
        );

        AgentLoopResult result = loop(gateway, List.of(searchTool)).run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.finalContent()).isEqualTo("基于证据的回答");
        assertThat(executions).containsExactly("Java并发");
        assertThat(result.trace().modelCalls()).hasSize(2);
        assertThat(result.trace().toolCalls()).hasSize(1);
        assertThat(result.trace().toolCalls().getFirst().status()).isEqualTo(ToolExecutionStatus.SUCCESS);
        assertThat(result.trace().totalUsage()).isEqualTo(new ModelUsage(130, 30, 160));

        ArgumentCaptor<ToolCallingRequest> captor = ArgumentCaptor.forClass(ToolCallingRequest.class);
        verify(gateway, times(2)).call(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(
                modelRequest -> assertThat(modelRequest.outputFormat()).isEqualTo(ModelOutputFormat.JSON_OBJECT)
        );
        List<ToolCallingMessage> secondRoundMessages = captor.getAllValues().get(1).messages();

        assertThat(secondRoundMessages).hasSize(4);
        assertThat(secondRoundMessages.get(2)).isEqualTo(new AssistantToolCallsMessage(List.of(toolCall)));
        assertThat(secondRoundMessages.get(3)).isInstanceOf(ToolResultMessage.class);

        ToolResultMessage toolResult = (ToolResultMessage) secondRoundMessages.get(3);
        assertThat(toolResult.toolCallId()).isEqualTo("call-1");
        assertThat(toolResult.toolName()).isEqualTo("search_tool");
        assertThat(toolResult.content()).contains("\"status\":\"SUCCESS\"", "evidence-Java并发");

        assertThat(result.toolResults()).hasSize(1);
        assertThat(result.toolResults().getFirst().toolCallId()).isEqualTo("call-1");
        assertThat(result.toolResults().getFirst().toolName()).isEqualTo("search_tool");
        assertThat(result.toolResults().getFirst().resultJson()).contains("evidence-Java并发");
    }

    @Test
    @DisplayName("同轮多个工具按模型顺序串行执行且单个失败不阻断剩余工具")
    void shouldContinueRemainingToolCallsAfterIndependentFailure() {
        List<String> executionOrder = new ArrayList<>();
        TestTool successTool = tool("success_tool", input -> {
            executionOrder.add("success:" + input.query());
            return "evidence-" + input.query();
        });
        TestTool failureTool = tool("failure_tool", input -> {
            executionOrder.add("failure:" + input.query());
            throw new ToolExecutionException(ToolExecutionErrorType.SCOPE_VIOLATION, "请求范围超出允许范围");
        });

        List<ToolCall> calls = List.of(
                new ToolCall("call-1", "success_tool", "{\"query\":\"A\"}"),
                new ToolCall("call-2", "failure_tool", "{\"query\":\"B\"}"),
                new ToolCall("call-3", "success_tool", "{\"query\":\"C\"}")
        );
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class))).thenReturn(
                new ToolCallsResult("request-1", "deepseek-v4-flash", calls, new ModelUsage(60, 20, 80)),
                finalAnswer("request-2", "已综合成功证据和失败信息")
        );

        AgentLoopResult result = loop(gateway, List.of(successTool, failureTool)).run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(executionOrder).containsExactly("success:A", "failure:B", "success:C");
        assertThat(result.trace().toolCalls()).extracting(trace -> trace.sequence()).containsExactly(1, 2, 3);
        assertThat(result.trace().toolCalls()).extracting(trace -> trace.status())
                .containsExactly(ToolExecutionStatus.SUCCESS, ToolExecutionStatus.FAILURE, ToolExecutionStatus.SUCCESS);
        assertThat(result.trace().toolCalls().get(1).errorType()).isEqualTo(ToolExecutionErrorType.SCOPE_VIOLATION);

        ArgumentCaptor<ToolCallingRequest> captor = ArgumentCaptor.forClass(ToolCallingRequest.class);
        verify(gateway, times(2)).call(captor.capture());
        List<ToolCallingMessage> secondRoundMessages = captor.getAllValues().get(1).messages();

        assertThat(secondRoundMessages).hasSize(6);
        assertThat(secondRoundMessages.get(2)).isEqualTo(new AssistantToolCallsMessage(calls));

        ToolResultMessage firstResult = (ToolResultMessage) secondRoundMessages.get(3);
        ToolResultMessage secondResult = (ToolResultMessage) secondRoundMessages.get(4);
        ToolResultMessage thirdResult = (ToolResultMessage) secondRoundMessages.get(5);

        assertThat(List.of(firstResult.toolCallId(), secondResult.toolCallId(), thirdResult.toolCallId()))
                .containsExactly("call-1", "call-2", "call-3");
        assertThat(firstResult.content()).contains("\"status\":\"SUCCESS\"");
        assertThat(secondResult.content()).contains("\"status\":\"FAILURE\"", "SCOPE_VIOLATION");
        assertThat(thirdResult.content()).contains("\"status\":\"SUCCESS\"");
        assertThat(result.toolResults())
                .extracting(ToolExecutionResult::toolCallId)
                .containsExactly("call-1", "call-2", "call-3");
    }

    @Test
    @DisplayName("达到最大模型迭代次数后不再发起下一轮模型调用")
    void shouldStopAtMaximumModelIterations() {
        List<String> executions = new ArrayList<>();
        TestTool tool = tool("search_tool", input -> {
            executions.add(input.query());
            return "evidence-" + input.query();
        });
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class))).thenReturn(
                toolCalls("request-1", "call-1", "A"),
                toolCalls("request-2", "call-2", "B")
        );

        AgentLoopResult result = loop(gateway, List.of(tool), policy(2, 6, 6, 2, 10_000)).run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.LIMIT_EXCEEDED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MAX_MODEL_ITERATIONS);
        assertThat(result.finalContent()).isNull();
        assertThat(result.trace().modelCalls()).hasSize(2);
        assertThat(result.trace().toolCalls()).hasSize(2);
        assertThat(executions).containsExactly("A", "B");
        verify(gateway, times(2)).call(any(ToolCallingRequest.class));
    }

    @Test
    @DisplayName("整轮工具调用超过总上限时不执行该轮任何工具")
    void shouldRejectWholeRoundWhenTotalToolCallLimitExceeded() {
        List<String> executions = new ArrayList<>();
        TestTool tool = tool("search_tool", input -> {
            executions.add(input.query());
            return "evidence-" + input.query();
        });
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class))).thenReturn(
                toolCalls("request-1", "call-1", "A"),
                new ToolCallsResult(
                        "request-2",
                        "deepseek-v4-flash",
                        List.of(
                                new ToolCall("call-2", "search_tool", "{\"query\":\"B\"}"),
                                new ToolCall("call-3", "search_tool", "{\"query\":\"C\"}")
                        ),
                        new ModelUsage(10, 5, 15)
                )
        );

        AgentLoopResult result = loop(gateway, List.of(tool), policy(5, 2, 2, 2, 10_000)).run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.LIMIT_EXCEEDED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MAX_TOTAL_TOOL_CALLS);
        assertThat(result.trace().modelCalls()).hasSize(2);
        assertThat(result.trace().toolCalls()).hasSize(1);
        assertThat(executions).containsExactly("A");
    }

    @Test
    @DisplayName("相同工具和规范化参数重复超过上限时终止循环")
    void shouldStopRepeatedToolCallsAcrossIterations() {
        AtomicInteger executions = new AtomicInteger();
        TestTool tool = tool("search_tool", input -> {
            executions.incrementAndGet();
            return "evidence";
        });
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class))).thenReturn(
                toolCalls("request-1", "call-1", "same"),
                toolCalls("request-2", "call-2", "same"),
                toolCalls("request-3", "call-3", "same")
        );

        AgentLoopResult result = loop(gateway, List.of(tool), policy(5, 6, 6, 2, 10_000)).run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.LIMIT_EXCEEDED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.REPEATED_TOOL_CALL);
        assertThat(result.trace().modelCalls()).hasSize(3);
        assertThat(result.trace().toolCalls()).hasSize(2);
        assertThat(executions).hasValue(2);
    }

    @Test
    @DisplayName("模型调用前估算和调用后核算均能触发Token软预算")
    void shouldEnforceTokenBudgetBeforeAndAfterModelCall() {
        TestTool tool = tool("search_tool", input -> "unused");
        AgentLoopPolicy tokenPolicy = policy(5, 6, 6, 2, 1_000);

        ToolCallingGateway preflightGateway = mock(ToolCallingGateway.class);
        AgentTokenEstimator overBudgetEstimator = (messages, definitions) -> new AgentInputEstimate(600, 100);

        AgentLoopResult preflightResult = loop(
                preflightGateway, List.of(tool), tokenPolicy, overBudgetEstimator, clock).run(request());

        assertThat(preflightResult.status()).isEqualTo(AgentRunStatus.BUDGET_EXCEEDED);
        assertThat(preflightResult.terminationReason()).isEqualTo(AgentTerminationReason.TOKEN_BUDGET_EXCEEDED);
        assertThat(preflightResult.trace().modelCalls()).isEmpty();
        verifyNoInteractions(preflightGateway);

        AtomicInteger executions = new AtomicInteger();
        TestTool unexecutedTool = tool("search_tool", input -> {
            executions.incrementAndGet();
            return "unused";
        });
        ToolCallingGateway postflightGateway = mock(ToolCallingGateway.class);
        when(postflightGateway.call(any(ToolCallingRequest.class))).thenReturn(
                new ToolCallsResult(
                        "request-1",
                        "deepseek-v4-flash",
                        List.of(new ToolCall("call-1", "search_tool", "{\"query\":\"Java\"}")),
                        new ModelUsage(800, 200, 1_000)
                )
        );
        AgentTokenEstimator smallEstimator = (messages, definitions) -> new AgentInputEstimate(10, 100);

        AgentLoopResult postflightResult = loop(
                postflightGateway, List.of(unexecutedTool), tokenPolicy, smallEstimator, clock).run(request());

        assertThat(postflightResult.status()).isEqualTo(AgentRunStatus.BUDGET_EXCEEDED);
        assertThat(postflightResult.terminationReason()).isEqualTo(AgentTerminationReason.TOKEN_BUDGET_EXCEEDED);
        assertThat(postflightResult.trace().modelCalls()).hasSize(1);
        assertThat(postflightResult.trace().toolCalls()).isEmpty();
        assertThat(postflightResult.trace().totalUsage()).isEqualTo(new ModelUsage(800, 200, 1_000));
        assertThat(executions).hasValue(0);
    }

    @Test
    @DisplayName("模型超时异常映射为确定性超时终态")
    void shouldMapModelTimeoutToTimedOutResult() {
        TestTool tool = tool("search_tool", input -> "unused");
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class)))
                .thenThrow(new ModelException(ModelErrorType.TIMEOUT, "模型调用超时"));

        AgentLoopResult result = loop(gateway, List.of(tool)).run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.TIMED_OUT);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MODEL_TIMEOUT);
        assertThat(result.trace().modelCalls()).hasSize(1);
        assertThat(result.trace().modelCalls().getFirst().outcome()).isEqualTo(AgentModelOutcome.FAILURE);
        assertThat(result.trace().modelCalls().getFirst().errorType()).isEqualTo(ModelErrorType.TIMEOUT);
        assertThat(result.trace().toolCalls()).isEmpty();
    }

    @Test
    @DisplayName("模型返回时总体Deadline已经到期则丢弃结果并终止")
    void shouldDiscardModelResultFinishedAfterAgentDeadline() {
        MutableClock mutableClock = new MutableClock(NOW, ZoneOffset.UTC);
        TestTool tool = tool("search_tool", input -> "unused");
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class))).thenAnswer(invocation -> {
            mutableClock.advance(Duration.ofSeconds(31));
            return finalAnswer("request-1", "迟到的回答");
        });

        AgentLoopResult result = loop(
                gateway, List.of(tool), policy(), new HeuristicAgentTokenEstimator(), mutableClock).run(request());

        assertThat(result.status()).isEqualTo(AgentRunStatus.TIMED_OUT);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.AGENT_DEADLINE_EXCEEDED);
        assertThat(result.finalContent()).isNull();
        assertThat(result.trace().modelCalls()).hasSize(1);
        assertThat(result.trace().durationMs()).isEqualTo(31_000);
    }

    @Test
    @DisplayName("只为白名单工具按执行顺序发送开始和完成观察事件")
    void shouldNotifyObserverForRegisteredToolInExecutionOrder() {
        TestTool searchTool = tool("search_tool", input -> "evidence-" + input.query());
        ToolCall toolCall = new ToolCall(
                "call-1",
                "search_tool",
                "{\"query\":\"Java并发\"}"
        );
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class))).thenReturn(
                new ToolCallsResult(
                        "request-1",
                        "deepseek-v4-flash",
                        List.of(toolCall),
                        new ModelUsage(50, 10, 60)
                ),
                finalAnswer("request-2", "基于工具证据的回答")
        );

        List<String> observedEvents = new ArrayList<>();
        AgentLoopObserver observer = new AgentLoopObserver() {
            @Override
            public void toolStarted(String toolName, Instant occurredAt) {
                observedEvents.add("STARTED:" + toolName);
            }

            @Override
            public void toolCompleted(
                    String toolName,
                    ToolExecutionStatus status,
                    Instant occurredAt
            ) {
                observedEvents.add("COMPLETED:" + toolName + ":" + status);
            }
        };

        AgentLoopResult result = loop(
                gateway,
                List.of(searchTool)
        ).run(request(), observer);

        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(observedEvents).containsExactly(
                "STARTED:search_tool",
                "COMPLETED:search_tool:SUCCESS"
        );
    }

    @Test
    @DisplayName("未知工具不会发送观察事件但仍返回受控工具失败结果")
    void shouldNotExposeUnknownToolThroughObserver() {
        TestTool allowedTool = tool("search_tool", input -> "unused");
        ToolCall unknownCall = new ToolCall(
                "call-unknown",
                "unknown_tool",
                "{\"query\":\"Java并发\"}"
        );
        ToolCallingGateway gateway = mock(ToolCallingGateway.class);
        when(gateway.call(any(ToolCallingRequest.class))).thenReturn(
                new ToolCallsResult(
                        "request-1",
                        "deepseek-v4-flash",
                        List.of(unknownCall),
                        new ModelUsage(50, 10, 60)
                ),
                finalAnswer("request-2", "无法使用未知工具")
        );

        List<String> observedEvents = new ArrayList<>();
        AgentLoopObserver observer = new AgentLoopObserver() {
            @Override
            public void toolStarted(String toolName, Instant occurredAt) {
                observedEvents.add("STARTED:" + toolName);
            }

            @Override
            public void toolCompleted(
                    String toolName,
                    ToolExecutionStatus status,
                    Instant occurredAt
            ) {
                observedEvents.add("COMPLETED:" + toolName);
            }
        };

        AgentLoopResult result = loop(
                gateway,
                List.of(allowedTool)
        ).run(request(), observer);

        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(observedEvents).isEmpty();
        assertThat(result.trace().toolCalls()).hasSize(1);
        assertThat(result.trace().toolCalls().getFirst().status())
                .isEqualTo(ToolExecutionStatus.FAILURE);
        assertThat(result.trace().toolCalls().getFirst().errorType())
                .isEqualTo(ToolExecutionErrorType.UNKNOWN_TOOL);
    }

    /** 创建包含默认策略和真实安全工具执行器的被测 Agent Loop。 */
    private AgentLoop loop(ToolCallingGateway gateway, List<AgentTool<?, ?>> tools) {
        return loop(gateway, tools, policy(), new HeuristicAgentTokenEstimator(), clock);
    }

    /** 创建使用指定限制策略的被测 Agent Loop。 */
    private AgentLoop loop(
            ToolCallingGateway gateway,
            List<AgentTool<?, ?>> tools,
            AgentLoopPolicy policy
    ) {
        return loop(gateway, tools, policy, new HeuristicAgentTokenEstimator(), clock);
    }

    /** 创建支持注入Token估算器和时钟的被测 Agent Loop。 */
    private AgentLoop loop(
            ToolCallingGateway gateway,
            List<AgentTool<?, ?>> tools,
            AgentLoopPolicy policy,
            AgentTokenEstimator tokenEstimator,
            Clock runtimeClock
    ) {
        ToolRegistry registry = new ToolRegistry(tools);
        SafeToolExecutor executor = new SafeToolExecutor(
                registry, jsonMapper, validatorFactory.getValidator(), executorService, runtimeClock);
        return new AgentLoop(
                gateway,
                registry,
                executor,
                tokenEstimator,
                new ToolCallFingerprintService(jsonMapper),
                policy,
                runtimeClock
        );
    }

    /** 创建不会触发边界限制的常规Loop策略。 */
    private AgentLoopPolicy policy() {
        return policy(5, 8, 4, 2, 10_000);
    }

    /** 创建具有指定迭代、调用和Token限制的Loop策略。 */
    private AgentLoopPolicy policy(
            int maxModelIterations,
            int maxTotalToolCalls,
            int maxCallsPerTool,
            int maxRepeatedCallCount,
            long maxTotalTokens
    ) {
        return new AgentLoopPolicy(
                maxModelIterations,
                maxTotalToolCalls,
                maxCallsPerTool,
                maxRepeatedCallCount,
                maxTotalTokens,
                500,
                50_000,
                Duration.ofSeconds(30),
                Duration.ofSeconds(10)
        );
    }

    /** 创建只包含一个工具调用的模型结果。 */
    private ToolCallsResult toolCalls(String requestId, String toolCallId, String query) {
        return new ToolCallsResult(
                requestId,
                "deepseek-v4-flash",
                List.of(new ToolCall(toolCallId, "search_tool", "{\"query\":\"" + query + "\"}")),
                new ModelUsage(10, 5, 15)
        );
    }
    /** 创建测试使用的 Agent 请求和服务端检索范围。 */
    private AgentLoopRequest request() {
        return new AgentLoopRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "只根据工具证据回答"),
                        new ToolCallingTextMessage(ModelRole.USER, "查询职业材料")
                ),
                new RetrievalScope("careerforge", Set.of(), Set.of()),
                ModelOutputFormat.JSON_OBJECT,
                "scope-v1"
        );
    }

    /** 创建测试使用的确定性只读工具。 */
    private TestTool tool(String name, Function<TestInput, String> action) {
        ToolContract<TestInput, String> contract = new ToolContract<>(
                new ToolDefinition(name, "测试工具", INPUT_SCHEMA),
                OUTPUT_SCHEMA,
                TestInput.class,
                String.class,
                ToolImplementationType.DETERMINISTIC,
                ToolRiskLevel.LOW,
                true,
                256,
                2048,
                5,
                Duration.ofSeconds(1)
        );
        return new TestTool(contract, action);
    }

    /** 创建测试使用的最终模型回答。 */
    private FinalAnswerResult finalAnswer(String requestId, String content) {
        return new FinalAnswerResult(
                requestId, "deepseek-v4-flash", content, new ModelUsage(80, 20, 100));
    }

    /**
     * @program: CareerForge-AI
     * @description: 表示测试工具接收的最小结构化参数。
     * @author: Miao Zheng
     * @date: 2026-08-06 18:30
     **/
    private record TestInput(@NotBlank String query) {
    }

    /**
     * @program: CareerForge-AI
     * @description: 提供可注入成功或失败行为的确定性测试工具。
     * @author: Miao Zheng
     * @date: 2026-08-06 18:30
     **/
    private record TestTool(
            ToolContract<TestInput, String> contract,
            Function<TestInput, String> action
    ) implements AgentTool<TestInput, String> {

        /** 执行测试注入的工具行为并生成标准输出。 */
        @Override
        public AgentToolOutput<String> execute(TestInput input, ToolExecutionContext context) {
            return AgentToolOutput.of(action.apply(input), 1);
        }
    }
    /**
     * @program: CareerForge-AI
     * @description: 为Deadline测试提供可确定推进的内存时钟。
     * @author: Miao Zheng
     * @date: 2026-08-06 19:05
     **/
    private static final class MutableClock extends Clock {

        private Instant current;
        private final ZoneId zone;

        private MutableClock(Instant current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        /** 返回测试时钟使用的时区。 */
        @Override
        public ZoneId getZone() {
            return zone;
        }

        /** 创建使用指定时区且保持当前时间的测试时钟。 */
        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(current, zone);
        }

        /** 返回当前可控时间。 */
        @Override
        public Instant instant() {
            return current;
        }

        /** 将当前时间向前推进指定时长。 */
        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}