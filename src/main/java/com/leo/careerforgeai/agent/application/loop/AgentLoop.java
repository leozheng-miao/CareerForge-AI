package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.agent.application.tool.AgentTool;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.domain.loop.AgentInputEstimate;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopRequest;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.AgentModelOutcome;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentModelCallTrace;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentToolCallTrace;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
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

    /** 执行一次不发布观察事件的非流式单Agent Loop。 */
    public AgentLoopResult run(AgentLoopRequest request) {
        return run(request, AgentLoopObserver.noOp());
    }

    /** 执行一次非流式单Agent Loop，并发布白名单工具的安全观察事件。 */
    public AgentLoopResult run(
            AgentLoopRequest request,
            AgentLoopObserver observer
    ) {
        Objects.requireNonNull(request, "request 不能为空");
        Objects.requireNonNull(observer, "observer 不能为空");

        String runId = UUID.randomUUID().toString();
        AgentRunState state = new AgentRunState(runId, policy, clock.instant());
        List<ToolCallingMessage> messages = new ArrayList<>(request.initialMessages());
        int toolSequence = 0;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                return terminate(state, AgentTerminationReason.INTERRUPTED);
            }

            AgentInputEstimate estimate = tokenEstimator.estimate(
                    messages,
                    toolRegistry.definitions()
            );

            Optional<AgentTerminationReason> preflightFailure =
                    state.checkBeforeModelCall(
                            estimate.estimatedInputTokens(),
                            estimate.messageHistoryChars(),
                            clock.instant()
                    );

            if (preflightFailure.isPresent()) {
                return terminate(state, preflightFailure.get());
            }

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
                modelResult = modelGateway.call(modelRequest);
            } catch (ModelException exception) {
                Instant failedAt = clock.instant();
                state.recordModelCall(new AgentModelCallTrace(
                        iteration,
                        null,
                        null,
                        AgentModelOutcome.FAILURE,
                        durationMs(modelStartedAt, failedAt),
                        estimate.estimatedInputTokens(),
                        null,
                        exception.getErrorType()
                ));

                AgentTerminationReason reason =
                        modelFailureReason(
                                state,
                                exception.getErrorType(),
                                failedAt
                        );

                log.warn(
                        "Agent 模型调用失败，runId={}, iteration={}, errorType={}",
                        runId,
                        iteration,
                        exception.getErrorType()
                );
                return terminate(state, reason);
            } catch (RuntimeException exception) {
                Instant failedAt = clock.instant();
                state.recordModelCall(new AgentModelCallTrace(
                        iteration,
                        null,
                        null,
                        AgentModelOutcome.FAILURE,
                        durationMs(modelStartedAt, failedAt),
                        estimate.estimatedInputTokens(),
                        null,
                        ModelErrorType.PROVIDER_ERROR
                ));

                log.warn(
                        "Agent 模型调用发生未知异常，runId={}, iteration={}, exceptionType={}",
                        runId,
                        iteration,
                        exception.getClass().getSimpleName()
                );
                return terminate(
                        state,
                        AgentTerminationReason.MODEL_FAILURE
                );
            }

            Instant modelFinishedAt = clock.instant();
            AgentModelOutcome modelOutcome =
                    modelResult instanceof FinalAnswerResult
                            ? AgentModelOutcome.FINAL_ANSWER
                            : AgentModelOutcome.TOOL_CALLS;

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
                return terminate(
                        state,
                        AgentTerminationReason.AGENT_DEADLINE_EXCEEDED
                );
            }

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
                return terminate(
                        state,
                        AgentTerminationReason.TOKEN_BUDGET_EXCEEDED
                );
            }

            ToolCallsResult toolCallsResult =
                    (ToolCallsResult) modelResult;

            Optional<AgentTerminationReason> toolLimitFailure =
                    state.registerToolCalls(
                            toolCallsResult.toolCalls(),
                            fingerprintService,
                            request.contextVersion()
                    );

            if (toolLimitFailure.isPresent()) {
                return terminate(state, toolLimitFailure.get());
            }

            messages.add(
                    new AssistantToolCallsMessage(
                            toolCallsResult.toolCalls()
                    )
            );

            ToolExecutionContext executionContext =
                    new ToolExecutionContext(
                            runId,
                            state.deadline(),
                            request.retrievalScope()
                    );

            for (ToolCall toolCall : toolCallsResult.toolCalls()) {
                if (Thread.currentThread().isInterrupted()) {
                    return terminate(
                            state,
                            AgentTerminationReason.INTERRUPTED
                    );
                }

                if (!clock.instant().isBefore(state.deadline())) {
                    return terminate(
                            state,
                            AgentTerminationReason.AGENT_DEADLINE_EXCEEDED
                    );
                }

                Optional<AgentTool<?, ?>> registeredTool =
                        toolRegistry.find(toolCall.name());

                Instant toolStartedAt = clock.instant();

                if (registeredTool.isPresent()) {
                    notifyToolStarted(
                            observer,
                            toolCall.name(),
                            toolStartedAt
                    );
                }

                ToolExecutionResult executionResult =
                        toolExecutor.execute(
                                toolCall,
                                executionContext
                        );

                Instant toolFinishedAt = clock.instant();

                if (registeredTool.isPresent()) {
                    notifyToolCompleted(
                            observer,
                            toolCall.name(),
                            executionResult.status(),
                            toolFinishedAt
                    );
                }

                toolSequence++;

                ToolImplementationType implementationType =
                        registeredTool
                                .map(AgentTool::contract)
                                .map(contract ->
                                        contract.implementationType()
                                )
                                .orElse(null);

                state.recordToolCall(new AgentToolCallTrace(
                        iteration,
                        toolSequence,
                        toolCall.id(),
                        toolCall.name(),
                        implementationType,
                        executionResult.status(),
                        durationMs(
                                toolStartedAt,
                                toolFinishedAt
                        ),
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
                    return terminate(
                            state,
                            AgentTerminationReason.INTERRUPTED
                    );
                }

                if (!toolFinishedAt.isBefore(state.deadline())) {
                    return terminate(
                            state,
                            AgentTerminationReason.AGENT_DEADLINE_EXCEEDED
                    );
                }

                if (state.isTokenBudgetExhausted()) {
                    return terminate(
                            state,
                            AgentTerminationReason.TOKEN_BUDGET_EXCEEDED
                    );
                }
            }
        }
    }

    private void notifyToolStarted(
            AgentLoopObserver observer,
            String toolName,
            Instant occurredAt
    ) {
        try {
            observer.toolStarted(toolName, occurredAt);
        } catch (RuntimeException exception) {
            log.warn(
                    "Agent工具开始观察事件发送失败，toolName={}, errorType={}",
                    toolName,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void notifyToolCompleted(
            AgentLoopObserver observer,
            String toolName,
            ToolExecutionStatus status,
            Instant occurredAt
    ) {
        try {
            observer.toolCompleted(
                    toolName,
                    status,
                    occurredAt
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Agent工具完成观察事件发送失败，toolName={}, errorType={}",
                    toolName,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private AgentTerminationReason modelFailureReason(
            AgentRunState state,
            ModelErrorType errorType,
            Instant failedAt
    ) {
        if (Thread.currentThread().isInterrupted()) {
            return AgentTerminationReason.INTERRUPTED;
        }

        if (!failedAt.isBefore(state.deadline())) {
            return AgentTerminationReason.AGENT_DEADLINE_EXCEEDED;
        }

        if (errorType == ModelErrorType.TIMEOUT) {
            return AgentTerminationReason.MODEL_TIMEOUT;
        }

        return AgentTerminationReason.MODEL_FAILURE;
    }

    private AgentLoopResult terminate(
            AgentRunState state,
            AgentTerminationReason reason
    ) {
        AgentRunStatus status = statusFor(reason);
        AgentRunTrace trace =
                state.snapshot(
                        status,
                        reason,
                        clock.instant()
                );

        return AgentLoopResult.terminated(
                status,
                reason,
                trace,
                state.toolResults()
        );
    }

    private AgentRunStatus statusFor(
            AgentTerminationReason reason
    ) {
        return switch (reason) {
            case AGENT_DEADLINE_EXCEEDED,
                 MODEL_TIMEOUT ->
                    AgentRunStatus.TIMED_OUT;

            case TOKEN_BUDGET_EXCEEDED ->
                    AgentRunStatus.BUDGET_EXCEEDED;

            case MAX_MODEL_ITERATIONS,
                 MAX_TOTAL_TOOL_CALLS,
                 MAX_CALLS_PER_TOOL,
                 REPEATED_TOOL_CALL,
                 MESSAGE_HISTORY_LIMIT_EXCEEDED ->
                    AgentRunStatus.LIMIT_EXCEEDED;

            case MODEL_FAILURE,
                 INTERRUPTED ->
                    AgentRunStatus.FAILED;

            case FINAL_ANSWER,
                 REFUSAL ->
                    throw new IllegalArgumentException(
                            "成功或拒答不能通过 terminated 创建"
                    );
        };
    }

    private long durationMs(
            Instant startedAt,
            Instant finishedAt
    ) {
        Duration duration =
                Duration.between(
                        startedAt,
                        finishedAt
                );

        return duration.isNegative()
                ? 0
                : duration.toMillis();
    }
}