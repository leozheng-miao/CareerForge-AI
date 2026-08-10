package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.agent.application.tool.AgentTool;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.domain.loop.AgentInputEstimate;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopRequest;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentModelCallTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentModelOutcome;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentToolCallTrace;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.domain.toolcalling.AssistantToolCallsMessage;
import com.leo.careerforgeai.model.domain.toolcalling.FinalAnswerResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallsResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolChoiceMode;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 手写编排模型请求、串行工具执行、消息回放、预算核算和确定性终止。
 * 关键 Trade-off：
 * 同轮 Tool Calls 在执行前整体完成次数和重复校验，避免执行一半才发现本轮已越界。
 * 工具业务失败不会终止 Loop；SafeToolExecutor 返回失败 Tool Result，让模型有一次修正机会。
 * Agent Deadline、线程中断或 Token 总预算属于全局边界，可以中止同轮剩余工具。
 * 最后一轮模型如果已产生及时的最终回答，即使真实 Token 略超软预算，仍返回成功并在 Trace 中如实记录。
 * 最终 finalContent 仍未经过 Career Coach 的结构化输出和引用校验。
 *
 * @author: Miao Zheng
 * @date: 2026-08-06 18:12
 **/
@Slf4j
public final class AgentLoop {

    private final ToolCallingGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final SafeToolExecutor toolExecutor;
    private final AgentTokenEstimator tokenEstimator;
    private final ToolCallFingerprintService fingerprintService;
    private final AgentLoopPolicy policy;
    private final Clock clock;

    public AgentLoop(
            ToolCallingGateway modelGateway,
            ToolRegistry toolRegistry,
            SafeToolExecutor toolExecutor,
            AgentTokenEstimator tokenEstimator,
            ToolCallFingerprintService fingerprintService,
            AgentLoopPolicy policy,
            Clock clock
    ) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway 不能为空");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry 不能为空");
        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor 不能为空");
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator 不能为空");
        this.fingerprintService = Objects.requireNonNull(fingerprintService, "fingerprintService 不能为空");
        this.policy = Objects.requireNonNull(policy, "policy 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /** 执行一次非流式单 Agent Loop，直到最终回答或确定性终止。 */
    public AgentLoopResult run(AgentLoopRequest request) {
        Objects.requireNonNull(request, "request 不能为空");

        // 1. 创建 runId
        String runId = UUID.randomUUID().toString();
        AgentRunState state = new AgentRunState(runId, policy, clock.instant());
        List<ToolCallingMessage> messages = new ArrayList<>(request.initialMessages());
        int toolSequence = 0;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                //2. 中断检查
                return terminate(state, AgentTerminationReason.INTERRUPTED);
            }

            //3. 估算token
            AgentInputEstimate estimate = tokenEstimator.estimate(messages, toolRegistry.definitions());
            //4. 检查总体 Deadline、最大迭代次数、最大输入字符数、累计 Token 软预算
            Optional<AgentTerminationReason> preflightFailure = state.checkBeforeModelCall(
                    estimate.estimatedInputTokens(), estimate.messageHistoryChars(), clock.instant());

            if (preflightFailure.isPresent()) return terminate(state, preflightFailure.get());

            //5. 开始迭代计数
            int iteration = state.startModelIteration();
            ToolCallingRequest modelRequest = new ToolCallingRequest(
                    messages,
                    toolRegistry.definitions(),
                    ToolChoiceMode.AUTO,
                    request.outputFormat(),
                    policy.maxOutputTokensPerModelCall(),
                    state.modelCallTimeout(clock.instant())
            );

            Instant modelStartedAt = clock.instant();
            ToolCallingModelResult modelResult;

            try {
                //6. 通过 Gateway 调用模型
                modelResult = modelGateway.call(modelRequest);
            } catch (ModelException exception) {
                Instant failedAt = clock.instant();
                state.recordModelCall(new AgentModelCallTrace(
                        iteration, null, null, AgentModelOutcome.FAILURE,
                        durationMs(modelStartedAt, failedAt), estimate.estimatedInputTokens(),
                        null, exception.getErrorType()
                ));

                AgentTerminationReason reason = modelFailureReason(state, exception.getErrorType(), failedAt);
                log.warn("Agent 模型调用失败，runId={}, iteration={}, errorType={}",
                        runId, iteration, exception.getErrorType());
                return terminate(state, reason);
            } catch (RuntimeException exception) {
                Instant failedAt = clock.instant();
                state.recordModelCall(new AgentModelCallTrace(
                        iteration, null, null, AgentModelOutcome.FAILURE,
                        durationMs(modelStartedAt, failedAt), estimate.estimatedInputTokens(),
                        null, ModelErrorType.PROVIDER_ERROR
                ));

                log.warn("Agent 模型调用发生未知异常，runId={}, iteration={}, exceptionType={}",
                        runId, iteration, exception.getClass().getSimpleName());
                return terminate(state, AgentTerminationReason.MODEL_FAILURE);
            }

            Instant modelFinishedAt = clock.instant();
            //7. 处理模型结果
            AgentModelOutcome modelOutcome = modelResult instanceof FinalAnswerResult
                    ? AgentModelOutcome.FINAL_ANSWER
                    : AgentModelOutcome.TOOL_CALLS;

            //8. 记录单轮 Agent 调用数据
            state.recordModelCall(new AgentModelCallTrace(
                    iteration,
                    modelResult.requestId(),
                    modelResult.model(),
                    modelOutcome,
                    durationMs(modelStartedAt, modelFinishedAt),
                    estimate.estimatedInputTokens(),
                    modelResult.usage(),
                    null
            ));

            if (!modelFinishedAt.isBefore(state.deadline())) {
                return terminate(state, AgentTerminationReason.AGENT_DEADLINE_EXCEEDED);
            }

            //9. 若为 最终结果 FinalAnswerResult，直接构建并返回
            if (modelResult instanceof FinalAnswerResult finalAnswer) {
                AgentRunTrace trace = state.snapshot(
                        AgentRunStatus.COMPLETED,
                        AgentTerminationReason.FINAL_ANSWER,
                        modelFinishedAt
                );
                return AgentLoopResult.completed(
                        finalAnswer.content(),
                        trace,
                        state.toolResults()
                );
            }

            if (state.isTokenBudgetExhausted()) {
                return terminate(state, AgentTerminationReason.TOKEN_BUDGET_EXCEEDED);
            }

            //10. 返回结果为 ToolCallsResult
            ToolCallsResult toolCallsResult = (ToolCallsResult) modelResult;
            Optional<AgentTerminationReason> toolLimitFailure = state.registerToolCalls(
                    toolCallsResult.toolCalls(),
                    fingerprintService,
                    request.contextVersion()
            );

            if (toolLimitFailure.isPresent()) return terminate(state, toolLimitFailure.get());

            //11. 将 Assistant message 加入消息历史
            messages.add(new AssistantToolCallsMessage(toolCallsResult.toolCalls()));
            ToolExecutionContext executionContext = new ToolExecutionContext(
                    runId,
                    state.deadline(),
                    request.retrievalScope()
            );

            for (ToolCall toolCall : toolCallsResult.toolCalls()) {
                if (Thread.currentThread().isInterrupted()) {
                    return terminate(state, AgentTerminationReason.INTERRUPTED);
                }
                if (!clock.instant().isBefore(state.deadline())) {
                    return terminate(state, AgentTerminationReason.AGENT_DEADLINE_EXCEEDED);
                }

                Instant toolStartedAt = clock.instant();
                //12. 对每个 tool 安全执行 并返回 ToolResult
                ToolExecutionResult executionResult = toolExecutor.execute(toolCall, executionContext);
                Instant toolFinishedAt = clock.instant();
                toolSequence++;

                //13. 找到已有注册的tool
                ToolImplementationType implementationType = toolRegistry.find(toolCall.name())
                        .map(AgentTool::contract)
                        .map(contract -> contract.implementationType())
                        .orElse(null);

                state.recordToolCall(new AgentToolCallTrace(
                        iteration,
                        toolSequence,
                        toolCall.id(),
                        toolCall.name(),
                        implementationType,
                        executionResult.status(),
                        durationMs(toolStartedAt, toolFinishedAt),
                        toolCall.argumentsJson().length(),
                        executionResult.resultJson().length(),
                        executionResult.resultCount(),
                        executionResult.errorType(),
                        executionResult.modelUsage(),
                        executionResult.modelDurationMs()
                ));
                state.recordToolResult(executionResult);

                messages.add(executionResult.toMessage());

                if (Thread.currentThread().isInterrupted()) {
                    return terminate(state, AgentTerminationReason.INTERRUPTED);
                }
                if (!toolFinishedAt.isBefore(state.deadline())) {
                    return terminate(state, AgentTerminationReason.AGENT_DEADLINE_EXCEEDED);
                }
                if (state.isTokenBudgetExhausted()) {
                    return terminate(state, AgentTerminationReason.TOKEN_BUDGET_EXCEEDED);
                }
            }
        }
    }

    /** 将模型异常和当前 Deadline 映射成确定性终止原因。 */
    private AgentTerminationReason modelFailureReason(
            AgentRunState state,
            ModelErrorType errorType,
            Instant failedAt
    ) {
        if (Thread.currentThread().isInterrupted()) return AgentTerminationReason.INTERRUPTED;
        if (!failedAt.isBefore(state.deadline())) return AgentTerminationReason.AGENT_DEADLINE_EXCEEDED;
        if (errorType == ModelErrorType.TIMEOUT) return AgentTerminationReason.MODEL_TIMEOUT;
        return AgentTerminationReason.MODEL_FAILURE;
    }

    /** 根据终止原因创建一致的业务状态和 Trace 快照。 */
    private AgentLoopResult terminate(AgentRunState state, AgentTerminationReason reason) {
        AgentRunStatus status = statusFor(reason);
        AgentRunTrace trace = state.snapshot(status, reason, clock.instant());
        return AgentLoopResult.terminated(
                status,
                reason,
                trace,
                state.toolResults()
        );
    }

    /** 将内部终止原因映射为对上层暴露的 Agent 状态。 */
    private AgentRunStatus statusFor(AgentTerminationReason reason) {
        return switch (reason) {
            case AGENT_DEADLINE_EXCEEDED, MODEL_TIMEOUT -> AgentRunStatus.TIMED_OUT;
            case TOKEN_BUDGET_EXCEEDED -> AgentRunStatus.BUDGET_EXCEEDED;
            case MAX_MODEL_ITERATIONS, MAX_TOTAL_TOOL_CALLS, MAX_CALLS_PER_TOOL,
                    REPEATED_TOOL_CALL, MESSAGE_HISTORY_LIMIT_EXCEEDED -> AgentRunStatus.LIMIT_EXCEEDED;
            case MODEL_FAILURE, INTERRUPTED -> AgentRunStatus.FAILED;
            case FINAL_ANSWER, REFUSAL -> throw new IllegalArgumentException(
                    "成功或拒答不能通过 terminated 创建");
        };
    }

    /** 计算非负毫秒耗时，避免系统时钟回拨产生负数。 */
    private long durationMs(Instant startedAt, Instant finishedAt) {
        Duration duration = Duration.between(startedAt, finishedAt);
        return duration.isNegative() ? 0 : duration.toMillis();
    }
}