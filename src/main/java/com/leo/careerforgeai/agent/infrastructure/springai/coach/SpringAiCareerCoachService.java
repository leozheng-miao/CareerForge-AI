package com.leo.careerforgeai.agent.infrastructure.springai.coach;

import com.leo.careerforgeai.agent.application.coach.CareerCoachDefinition;
import com.leo.careerforgeai.agent.application.coach.CareerCoachFinalAnswerValidator;
import com.leo.careerforgeai.agent.application.coach.CareerCoachScopeProvider;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.SpringAiToolLoopLimitException;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.SpringAiToolRunContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用Spring AI ChatClient默认Tool Calling生命周期执行Career Coach对照链路。
 * @author: Miao Zheng
 * @date: 2026-08-10 02:10
 **/
public final class SpringAiCareerCoachService {

    private final ChatClient chatClient;
    private final CareerCoachFinalAnswerValidator finalAnswerValidator;
    private final RetrievalScope serverRetrievalScope;
    private final List<ToolCallback> toolCallbacks;
    private final AgentLoopPolicy policy;
    private final Clock clock;

    public SpringAiCareerCoachService(
            ChatClient chatClient,
            CareerCoachFinalAnswerValidator finalAnswerValidator,
            CareerCoachScopeProvider scopeProvider,
            List<ToolCallback> toolCallbacks,
            AgentLoopPolicy policy,
            Clock clock
    ) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient不能为空");
        this.finalAnswerValidator = Objects.requireNonNull(finalAnswerValidator, "finalAnswerValidator不能为空");
        this.serverRetrievalScope = Objects.requireNonNull(scopeProvider, "scopeProvider不能为空").scope();
        if (toolCallbacks == null || toolCallbacks.isEmpty() || toolCallbacks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("toolCallbacks不能为空且不能包含null");
        }
        this.toolCallbacks = List.copyOf(toolCallbacks);
        this.policy = Objects.requireNonNull(policy, "policy不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    /**
     * 执行一次Spring AI Career Coach对照请求并返回经过共享引用校验的结果。
     */
    public SpringAiCareerCoachResult coach(String message) {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message不能为空");
        if (message.length() > CareerCoachDefinition.MAX_USER_MESSAGE_CHARS) {
            throw new IllegalArgumentException("message长度不能超过12000");
        }

        Instant startedAt = clock.instant();
        String runId = UUID.randomUUID().toString();
        ToolExecutionContext executionContext = new ToolExecutionContext(
                runId, startedAt.plus(policy.totalTimeout()), serverRetrievalScope);
        SpringAiToolRunContext runContext = new SpringAiToolRunContext(executionContext);

        String finalContent;
        try {
            finalContent = chatClient.prompt()
                    .system(CareerCoachDefinition.SYSTEM_PROMPT)
                    .user(message.strip())
                    .options(DeepSeekChatOptions.builder()
                            .maxTokens(policy.maxOutputTokensPerModelCall())
                            .toolChoice("auto")
                            .responseFormat(ResponseFormat.builder()
                                    .type(ResponseFormat.Type.JSON_OBJECT)
                                    .build()))
                    .tools(toolCallbacks)
                    .toolContext(runContext.asToolContextMap())
                    .call()
                    .content();
        } catch (SpringAiToolLoopLimitException exception) {
            throw executionFailure(runId, SpringAiCareerCoachErrorType.LIMIT_EXCEEDED,
                    runContext, exception);
        } catch (TransientAiException exception) {
            throw executionFailure(runId, SpringAiCareerCoachErrorType.TRANSIENT_MODEL_FAILURE,
                    runContext, exception);
        } catch (NonTransientAiException exception) {
            throw executionFailure(runId, SpringAiCareerCoachErrorType.NON_TRANSIENT_MODEL_FAILURE,
                    runContext, exception);
        } catch (ToolExecutionException exception) {
            throw executionFailure(runId, SpringAiCareerCoachErrorType.TOOL_EXECUTION_FAILURE,
                    runContext, exception);
        } catch (RuntimeException exception) {
            throw executionFailure(runId, SpringAiCareerCoachErrorType.FRAMEWORK_FAILURE,
                    runContext, exception);
        }

        CareerCoachAnswer answer = finalAnswerValidator.validate(finalContent, runContext.results());
        long totalDurationMs = Math.max(0, Duration.between(startedAt, clock.instant()).toMillis());
        return new SpringAiCareerCoachResult(answer, runId, runContext.results(), totalDurationMs);
    }

    private SpringAiCareerCoachExecutionException executionFailure(
            String runId,
            SpringAiCareerCoachErrorType errorType,
            SpringAiToolRunContext runContext,
            RuntimeException cause
    ) {
        return new SpringAiCareerCoachExecutionException(
                runId, errorType, runContext.results(), cause);
    }
}