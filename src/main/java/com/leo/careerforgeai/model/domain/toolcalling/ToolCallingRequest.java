package com.leo.careerforgeai.model.domain.toolcalling;

import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRole;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 保存一次模型调用需要的消息历史、工具定义、选择模式、输出预算和超时。
 * @author: Miao Zheng
 * @date: 2026-08-06 16:47
 */
public record ToolCallingRequest(
        List<ToolCallingMessage> messages,
        List<ToolDefinition> tools,
        ToolChoiceMode toolChoiceMode,
        ModelOutputFormat outputFormat,
        int maxOutputTokens,
        Duration timeout
) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public ToolCallingRequest {
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("messages 不能为空");
        if (messages.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("messages 不能包含 null");
        if (tools == null || tools.isEmpty()) throw new IllegalArgumentException("tools 不能为空");
        if (tools.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("tools 不能包含 null");
        if (toolChoiceMode == null) throw new IllegalArgumentException("toolChoiceMode 不能为空");
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens 必须大于 0");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout 必须大于 0");
        }
        if (outputFormat == null) throw new IllegalArgumentException("outputFormat 不能为空");

        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        validateUniqueToolNames(tools);
        validateMessageHistory(messages);
    }

    public ToolCallingRequest(
            List<ToolCallingMessage> messages,
            List<ToolDefinition> tools,
            ToolChoiceMode toolChoiceMode,
            ModelOutputFormat outputFormat,
            int maxOutputTokens
    ) {
        this(messages, tools, toolChoiceMode, outputFormat, maxOutputTokens, DEFAULT_TIMEOUT);
    }

    public ToolCallingRequest withTimeout(Duration timeout) {
        return new ToolCallingRequest(
                messages,
                tools,
                toolChoiceMode,
                outputFormat,
                maxOutputTokens,
                timeout
        );
    }

    private static void validateUniqueToolNames(List<ToolDefinition> tools) {
        Set<String> names = new HashSet<>();
        for (ToolDefinition tool : tools) {
            if (!names.add(tool.name())) throw new IllegalArgumentException("存在重复工具名称=" + tool.name());
        }
    }

    private static void validateMessageHistory(List<ToolCallingMessage> messages) {
        if (!(messages.getFirst() instanceof ToolCallingTextMessage first) || first.role() != ModelRole.SYSTEM) {
            throw new IllegalArgumentException("第一条消息必须是 SYSTEM");
        }

        boolean userMessageFound = false;
        for (int index = 0; index < messages.size(); index++) {
            ToolCallingMessage message = messages.get(index);

            if (message instanceof ToolCallingTextMessage textMessage) {
                if (index > 0 && textMessage.role() == ModelRole.SYSTEM) {
                    throw new IllegalArgumentException("SYSTEM 消息只能位于第一条");
                }
                if (textMessage.role() == ModelRole.USER) userMessageFound = true;
                continue;
            }

            if (message instanceof ToolResultMessage resultMessage) {
                throw new IllegalArgumentException("存在无法关联的 Tool Result ID=" + resultMessage.toolCallId());
            }

            AssistantToolCallsMessage assistantMessage = (AssistantToolCallsMessage) message;
            List<ToolCall> calls = assistantMessage.toolCalls();

            if (index + calls.size() >= messages.size()) {
                throw new IllegalArgumentException("Assistant Tool Calls 存在遗漏的 Tool Result");
            }

            for (int callIndex = 0; callIndex < calls.size(); callIndex++) {
                ToolCall call = calls.get(callIndex);
                ToolCallingMessage candidate = messages.get(index + callIndex + 1);

                if (!(candidate instanceof ToolResultMessage result)) {
                    throw new IllegalArgumentException("Tool Call 缺少对应结果，ID=" + call.id());
                }
                if (!call.id().equals(result.toolCallId())) {
                    throw new IllegalArgumentException("Tool Call ID 与 Tool Result 不匹配，expected=" + call.id());
                }
                if (!call.name().equals(result.toolName())) {
                    throw new IllegalArgumentException("Tool Call 工具名与 Tool Result 不匹配，ID=" + call.id());
                }
            }

            index += calls.size();
        }

        if (!userMessageFound) throw new IllegalArgumentException("至少需要一条 USER 消息");
    }
}