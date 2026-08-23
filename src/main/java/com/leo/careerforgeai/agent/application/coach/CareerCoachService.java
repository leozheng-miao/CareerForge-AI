package com.leo.careerforgeai.agent.application.coach;

import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerValidator;
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

    public CareerCoachService(
            AgentLoop agentLoop,
            CareerCoachFinalAnswerValidator finalAnswerValidator,
            CareerCoachScopeProvider scopeProvider
    ) {
        this.agentLoop = Objects.requireNonNull(agentLoop, "agentLoop不能为空");
        this.finalAnswerValidator = Objects.requireNonNull(finalAnswerValidator, "finalAnswerValidator不能为空");
        this.serverRetrievalScope = Objects.requireNonNull(scopeProvider, "scopeProvider不能为空").scope();
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
            log.warn(
                    "Career Coach未完成，runId={}, status={}, terminationReason={}",
                    loopResult.trace().runId(),
                    loopResult.status(),
                    loopResult.terminationReason()
            );
            throw new CareerCoachExecutionException(loopResult);
        }

        try {
            CareerCoachAnswer answer = finalAnswerValidator.validate(loopResult);
            log.info(
                    "Career Coach完成，runId={}, answerStatus={}, modelCalls={}, toolCalls={}, totalTokens={}",
                    loopResult.trace().runId(),
                    answer.status(),
                    loopResult.trace().modelCalls().size(),
                    loopResult.trace().toolCalls().size(),
                    loopResult.trace().totalUsage().totalTokens()
            );
            return new CareerCoachResult(answer, loopResult.trace());
        } catch (CareerCoachFinalAnswerException exception) {
            log.warn(
                    "Career Coach最终回答校验失败，runId={}, errorType={}",
                    loopResult.trace().runId(),
                    exception.getErrorType()
            );
            throw exception.withTrace(loopResult.trace());
        }
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