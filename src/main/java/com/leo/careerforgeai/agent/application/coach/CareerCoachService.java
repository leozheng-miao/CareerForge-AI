package com.leo.careerforgeai.agent.application.coach;

import com.leo.careerforgeai.agent.application.loop.AgentLoop;
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

/**
 * @program: CareerForge-AI
 * @description: 使用服务端Prompt和RetrievalScope编排Career Coach单Agent并返回可信回答。
 * @author: Miao Zheng
 * @date: 2026-08-07 05:30
 **/
@Service
@Slf4j
public final class CareerCoachService {

    private static final String CONTEXT_VERSION = CareerCoachDefinition.CONTEXT_VERSION;

    private final AgentLoop agentLoop;
    private final CareerCoachFinalAnswerValidator finalAnswerValidator;
    private final RetrievalScope serverRetrievalScope;

    /**
     * 注入Agent编排组件并根据服务端知识文档配置构造不可扩大的检索范围。
     */
    public CareerCoachService(
            AgentLoop agentLoop,
            CareerCoachFinalAnswerValidator finalAnswerValidator,
            CareerCoachScopeProvider scopeProvider
    ) {
        this.agentLoop = agentLoop;
        this.finalAnswerValidator = finalAnswerValidator;
        this.serverRetrievalScope = scopeProvider.scope();
    }

    /**
     * 执行一次非流式Career Coach请求并返回经过引用白名单校验的可信结果。
     */
    public CareerCoachResult coach(String message) {
        String normalizedMessage = normalizeUserMessage(message);
        return execute(List.of(
                new ToolCallingTextMessage(ModelRole.SYSTEM, CareerCoachDefinition.SYSTEM_PROMPT),
                new ToolCallingTextMessage(ModelRole.USER, normalizedMessage)
        ));
    }

    /**
     * 使用已经完成用户隔离、状态过滤和预算裁剪的结构化Context执行Career Coach。
     * 该方法不得直接暴露为接收客户端ConversationContext的Controller接口。
     */
    public CareerCoachResult coachWithContext(ConversationContext context) {
        if (context == null) throw new IllegalArgumentException("context不能为空");

        List<ToolCallingTextMessage> initialMessages =
                new ArrayList<>(context.usage().messageCount() + 1);

        initialMessages.add(
                new ToolCallingTextMessage(ModelRole.SYSTEM, CareerCoachDefinition.SYSTEM_PROMPT)
        );

        if (!context.confirmedMemories().isEmpty()) {
            initialMessages.add(new ToolCallingTextMessage(
                    ModelRole.USER,
                    ConfirmedMemoryContextFormatter.format(
                            context.confirmedMemories()
                    )
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

        return execute(initialMessages);
    }

    /**
     * 执行公共Agent Loop、终态检查和最终回答安全校验。
     */
    private CareerCoachResult execute(List<ToolCallingTextMessage> initialMessages) {
        AgentLoopRequest loopRequest = new AgentLoopRequest(
                initialMessages,
                serverRetrievalScope,
                ModelOutputFormat.JSON_OBJECT,
                CONTEXT_VERSION
        );

        AgentLoopResult loopResult = agentLoop.run(loopRequest);
        if (loopResult.status() != AgentRunStatus.COMPLETED) {
            log.warn("Career Coach未完成，runId={}, status={}, terminationReason={}",
                    loopResult.trace().runId(), loopResult.status(), loopResult.terminationReason());
            throw new CareerCoachExecutionException(loopResult);
        }

        try {
            CareerCoachAnswer answer = finalAnswerValidator.validate(loopResult);
            log.info("Career Coach完成，runId={}, answerStatus={}, modelCalls={}, toolCalls={}, totalTokens={}",
                    loopResult.trace().runId(), answer.status(), loopResult.trace().modelCalls().size(),
                    loopResult.trace().toolCalls().size(), loopResult.trace().totalUsage().totalTokens());
            return new CareerCoachResult(answer, loopResult.trace());
        } catch (CareerCoachFinalAnswerException exception) {
            log.warn("Career Coach最终回答校验失败，runId={}, errorType={}",
                    loopResult.trace().runId(), exception.getErrorType());
            throw exception.withTrace(loopResult.trace());
        }
    }


    /**
     * 校验并标准化当前用户消息。
     */
    private String normalizeUserMessage(String message) {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message不能为空");
        if (message.length() > CareerCoachDefinition.MAX_USER_MESSAGE_CHARS) {
            throw new IllegalArgumentException("message长度不能超过12000");
        }
        return message.strip();
    }
}