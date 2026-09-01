package com.leo.careerforgeai.agent.application.coach;

import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerValidator;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachStructuredOutputRepairer;
import com.leo.careerforgeai.agent.application.loop.AgentLoop;
import com.leo.careerforgeai.agent.application.loop.AgentLoopObserver;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopRequest;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.memory.application.context.ConfirmedMemoryContextFormatter;
import com.leo.careerforgeai.memory.application.context.ConversationContext;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachStructuredOutputRepairer;
import com.leo.careerforgeai.agent.domain.loop.AgentModelOutcome;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentModelCallTrace;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.model.domain.ModelResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;

/**
 * @program: CareerForge-AI
 * @description: 使用服务端Prompt和RetrievalScope编排Career Coach并校验最终回答
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@Service
@Slf4j
public final class CareerCoachService {

    private static final String CONTEXT_VERSION = CareerCoachDefinition.CONTEXT_VERSION;

    private final AgentLoop agentLoop;
    private final CareerCoachFinalAnswerValidator finalAnswerValidator;
    private final RetrievalScope serverRetrievalScope;
    private final CareerCoachStructuredOutputRepairer outputRepairer;
    private final Clock clock;

    public CareerCoachService(
            AgentLoop agentLoop,
            CareerCoachFinalAnswerValidator finalAnswerValidator,
            CareerCoachScopeProvider scopeProvider,
            CareerCoachStructuredOutputRepairer outputRepairer,
            Clock clock
    ) {
        this.agentLoop = Objects.requireNonNull(agentLoop, "agentLoop不能为空");
        this.finalAnswerValidator = Objects.requireNonNull(finalAnswerValidator, "finalAnswerValidator不能为空");
        this.serverRetrievalScope = Objects.requireNonNull(scopeProvider, "scopeProvider不能为空").scope();
        this.outputRepairer = Objects.requireNonNull(outputRepairer, "outputRepairer不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public CareerCoachResult coach(String message) {
        String normalizedMessage = normalizeUserMessage(message);
        return execute(List.of(
                new ToolCallingTextMessage(ModelRole.SYSTEM, CareerCoachDefinition.SYSTEM_PROMPT),
                new ToolCallingTextMessage(ModelRole.USER, normalizedMessage)
        ));
    }

    public CareerCoachResult coachWithContext(ConversationContext context) {
        return execute(contextMessages(context));
    }

    public CareerCoachResult coachWithContext(
            ConversationContext context,
            AgentLoopObserver observer
    ) {
        Objects.requireNonNull(observer, "observer不能为空");
        return execute(contextMessages(context), observer);
    }

    private List<ToolCallingTextMessage> contextMessages(ConversationContext context) {
        Objects.requireNonNull(context, "context不能为空");

        List<ToolCallingTextMessage> initialMessages =
                new ArrayList<>(context.usage().messageCount() + 1);

        initialMessages.add(
                new ToolCallingTextMessage(ModelRole.SYSTEM, CareerCoachDefinition.SYSTEM_PROMPT)
        );

        if (!context.confirmedMemories().isEmpty()) {
            initialMessages.add(new ToolCallingTextMessage(
                    ModelRole.USER,
                    ConfirmedMemoryContextFormatter.format(context.confirmedMemories())
            ));
        }

        for (ConversationContext.ConversationExchange exchange : context.recentExchanges()) {
            initialMessages.add(
                    new ToolCallingTextMessage(ModelRole.USER, exchange.userMessage())
            );
            initialMessages.add(
                    new ToolCallingTextMessage(ModelRole.ASSISTANT, exchange.assistantMessage())
            );
        }

        initialMessages.add(new ToolCallingTextMessage(
                ModelRole.USER,
                normalizeUserMessage(context.currentMessage())
        ));

        if (initialMessages.size() != context.usage().messageCount() + 1) {
            throw new IllegalStateException("ConversationContext消息数量映射错误");
        }
        return List.copyOf(initialMessages);
    }

    private CareerCoachResult execute(List<ToolCallingTextMessage> initialMessages) {
        return finish(agentLoop.run(createLoopRequest(initialMessages)));
    }

    private CareerCoachResult execute(
            List<ToolCallingTextMessage> initialMessages,
            AgentLoopObserver observer
    ) {
        return finish(agentLoop.run(createLoopRequest(initialMessages), observer));
    }

    private AgentLoopRequest createLoopRequest(
            List<ToolCallingTextMessage> initialMessages
    ) {
        return new AgentLoopRequest(
                initialMessages,
                serverRetrievalScope,
                ModelOutputFormat.JSON_OBJECT,
                CONTEXT_VERSION
        );
    }

    private CareerCoachResult finish(AgentLoopResult loopResult) {
        if (loopResult.status() != AgentRunStatus.COMPLETED) {
            log.warn("Career Coach未完成，runId={}, status={}, terminationReason={}",
                    loopResult.trace().runId(), loopResult.status(), loopResult.terminationReason());
            throw new CareerCoachExecutionException(loopResult);
        }

        try {
            CareerCoachAnswer answer = finalAnswerValidator.validate(loopResult);
            logCompleted(loopResult.trace(), answer, false);
            return new CareerCoachResult(answer, loopResult.trace());
        } catch (CareerCoachFinalAnswerException exception) {
            logValidationFailure(loopResult.trace().runId(), exception, false);
            if (!outputRepairer.supports(exception)) throw exception.withTrace(loopResult.trace());
            return repairOnce(loopResult, exception);
        }
    }

    private CareerCoachResult repairOnce(
            AgentLoopResult loopResult,
            CareerCoachFinalAnswerException originalFailure
    ) {
        long startedAt = System.nanoTime();
        ModelResponse response;
        try {
            response = outputRepairer.repair(loopResult.finalContent(), originalFailure);
        } catch (RuntimeException exception) {
            log.warn("Career Coach结构修复调用失败，runId={}, originalStage={}, repairAttempt=1, failureType={}",
                    loopResult.trace().runId(), originalFailure.getFailureStage(),
                    exception.getClass().getSimpleName());
            throw originalFailure.withTrace(loopResult.trace());
        }

        long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        AgentRunTrace repairedTrace = appendRepairTrace(
                loopResult.trace(), response, durationMs, loopResult.finalContent()
        );

        try {
            CareerCoachAnswer answer = finalAnswerValidator.validate(
                    response.content(), loopResult.toolResults()
            );
            log.info("Career Coach结构修复成功，runId={}, repairRequestId={}, repairAttempt=1, repairTokens={}, repairDurationMs={}",
                    repairedTrace.runId(), response.requestId(), response.usage().totalTokens(), durationMs);
            logCompleted(repairedTrace, answer, true);
            return new CareerCoachResult(answer, repairedTrace);
        } catch (CareerCoachFinalAnswerException exception) {
            logValidationFailure(repairedTrace.runId(), exception, true);
            throw exception.withTrace(repairedTrace);
        }
    }

    private AgentRunTrace appendRepairTrace(
            AgentRunTrace original,
            ModelResponse response,
            long durationMs,
            String invalidOutput
    ) {
        List<AgentModelCallTrace> modelCalls = new ArrayList<>(original.modelCalls());
        int iteration = modelCalls.stream().mapToInt(AgentModelCallTrace::iteration).max().orElse(0) + 1;
        long estimatedInputTokens = Math.max(1, (invalidOutput.length() + 1L) / 2L);
        modelCalls.add(new AgentModelCallTrace(
                iteration,
                response.requestId(),
                response.model(),
                AgentModelOutcome.STRUCTURED_REPAIR,
                durationMs,
                estimatedInputTokens,
                response.usage(),
                null
        ));

        Instant finishedAt = clock.instant();
        if (finishedAt.isBefore(original.finishedAt())) finishedAt = original.finishedAt();
        return new AgentRunTrace(
                original.runId(),
                original.startedAt(),
                finishedAt,
                original.status(),
                original.terminationReason(),
                modelCalls,
                original.toolCalls()
        );
    }

    private void logCompleted(AgentRunTrace trace, CareerCoachAnswer answer, boolean repaired) {
        log.info("Career Coach完成，runId={}, answerStatus={}, modelCalls={}, toolCalls={}, repaired={}, totalTokens={}",
                trace.runId(), answer.status(), trace.modelCalls().size(),
                trace.toolCalls().size(), repaired, trace.totalUsage().totalTokens());
    }

    private void logValidationFailure(
            String runId,
            CareerCoachFinalAnswerException exception,
            boolean repairAttempt
    ) {
        log.warn("Career Coach最终回答校验失败，runId={}, errorType={}, failureStage={}, failureReason={}, fieldPath={}, repairAttempt={}, outputChars={}, outputSha256={}",
                runId, exception.getErrorType(), exception.getFailureStage(),
                exception.getFailureReason(), exception.getFieldPath(), repairAttempt ? 1 : 0,
                exception.getOutputChars(), exception.getOutputSha256());
    }

    private String normalizeUserMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message不能为空");
        }
        if (message.length() > CareerCoachDefinition.MAX_USER_MESSAGE_CHARS) {
            throw new IllegalArgumentException("message长度不能超过12000");
        }
        return message.strip();
    }
}