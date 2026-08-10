package com.leo.careerforgeai.agent.infrastructure.springai.tool.lifecycle;

import com.leo.careerforgeai.agent.application.loop.ToolCallFingerprintService;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 在Spring AI默认工具执行前增加总调用、单工具和重复调用限制。
 * @author: Miao Zheng
 * @date: 2026-08-10 05:50
 **/
public final class SpringAiBoundedToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;
    private final AgentLoopPolicy policy;
    private final ToolCallFingerprintService fingerprintService;
    private final String contextVersion;

    public SpringAiBoundedToolCallingManager(
            ToolCallingManager delegate,
            AgentLoopPolicy policy,
            ToolCallFingerprintService fingerprintService,
            String contextVersion
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为空");
        this.policy = Objects.requireNonNull(policy, "policy不能为空");
        this.fingerprintService = Objects.requireNonNull(
                fingerprintService, "fingerprintService不能为空");
        if (contextVersion == null || contextVersion.isBlank()) {
            throw new IllegalArgumentException("contextVersion不能为空");
        }
        this.contextVersion = contextVersion;
    }

    /** 继续复用Spring AI默认Manager解析模型可见工具定义。 */
    @Override
    public List<ToolDefinition> resolveToolDefinitions(
            ToolCallingChatOptions chatOptions
    ) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    /** 校验整批Tool Calls后再委托Spring AI执行和组装Tool Result消息。 */
    @Override
    public ToolExecutionResult executeToolCalls(
            Prompt prompt,
            ChatResponse chatResponse
    ) {
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions options)) {
            throw new IllegalArgumentException("Spring AI工具调用缺少ToolCallingChatOptions");
        }
        if (chatResponse == null
                || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            throw new IllegalArgumentException("Spring AI工具调用响应不完整");
        }

        SpringAiToolRunContext runContext = SpringAiToolRunContext.requireFrom(
                new ToolContext(options.getToolContext())
        );
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        List<ToolCall> toolCalls = assistantMessage.getToolCalls().stream()
                .map(call -> new ToolCall(
                        call.id(),
                        call.name(),
                        normalizedArguments(call.arguments())
                ))
                .toList();

        runContext.registerToolCalls(
                toolCalls,
                policy,
                fingerprintService,
                contextVersion
        );
        return delegate.executeToolCalls(prompt, chatResponse);
    }

    private String normalizedArguments(String arguments) {
        return arguments == null || arguments.isBlank() ? "{}" : arguments;
    }
}