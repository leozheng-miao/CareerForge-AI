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
 * @description: 保存一次模型调用需要的消息、工具、输出格式、采样参数、预算和超时。
 * @author: Miao Zheng
 * @date: 2026-08-06 16:47
 * @param messages 消息历史
 * @param tools 服务端允许的工具定义
 * @param toolChoiceMode 工具选择模式
 * @param outputFormat 输出格式
 * @param maxOutputTokens 最大输出Token
 * @param temperature 服务端控制的采样温度
 * @param timeout 单次调用超时
 */
public record ToolCallingRequest(
        List<ToolCallingMessage> messages,
        List<ToolDefinition> tools,
        ToolChoiceMode toolChoiceMode,
        ModelOutputFormat outputFormat,
        int maxOutputTokens,
        double temperature,
        Duration timeout
) {

    private static final double DEFAULT_TEMPERATURE = 1.0;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public ToolCallingRequest {
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("messages不能为空");
        if (messages.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("messages不能包含null");
        if (tools == null || tools.isEmpty()) throw new IllegalArgumentException("tools不能为空");
        if (tools.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("tools不能包含null");
        if (toolChoiceMode == null) throw new IllegalArgumentException("toolChoiceMode不能为空");
        if (outputFormat == null) throw new IllegalArgumentException("outputFormat不能为空");
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens必须大于0");
        if (!Double.isFinite(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("temperature必须在0到2之间");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout必须大于0");
        }
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        validateUniqueToolNames(tools);
        validateMessageHistory(messages);
    }

    public ToolCallingRequest(List<ToolCallingMessage> messages, List<ToolDefinition> tools,
                              ToolChoiceMode toolChoiceMode, ModelOutputFormat outputFormat,
                              int maxOutputTokens, Duration timeout) {
        this(messages, tools, toolChoiceMode, outputFormat,
                maxOutputTokens, DEFAULT_TEMPERATURE, timeout);
    }

    public ToolCallingRequest(List<ToolCallingMessage> messages, List<ToolDefinition> tools,
                              ToolChoiceMode toolChoiceMode, ModelOutputFormat outputFormat,
                              int maxOutputTokens) {
        this(messages, tools, toolChoiceMode, outputFormat,
                maxOutputTokens, DEFAULT_TEMPERATURE, DEFAULT_TIMEOUT);
    }

    public ToolCallingRequest withTimeout(Duration timeout) {
        return new ToolCallingRequest(messages, tools, toolChoiceMode, outputFormat,
                maxOutputTokens, temperature, timeout);
    }

    public ToolCallingRequest withTemperature(double temperature) {
        return new ToolCallingRequest(messages, tools, toolChoiceMode, outputFormat,
                maxOutputTokens, temperature, timeout);
    }

    private static void validateUniqueToolNames(List<ToolDefinition> tools) {
        Set<String> names = new HashSet<>();
        for (ToolDefinition tool : tools) {
            if (!names.add(tool.name())) throw new IllegalArgumentException("存在重复工具名称=" + tool.name());
        }
    }

    private static void validateMessageHistory(List<ToolCallingMessage> messages) {
        if (!(messages.getFirst() instanceof ToolCallingTextMessage first)
                || first.role() != ModelRole.SYSTEM) {
            throw new IllegalArgumentException("第一条消息必须是SYSTEM");
        }

        boolean userMessageFound = false;
        for (int index = 0; index < messages.size(); index++) {
            ToolCallingMessage message = messages.get(index);
            if (message instanceof ToolCallingTextMessage textMessage) {
                if (index > 0 && textMessage.role() == ModelRole.SYSTEM) {
                    throw new IllegalArgumentException("SYSTEM消息只能位于第一条");
                }
                if (textMessage.role() == ModelRole.USER) userMessageFound = true;
                continue;
            }
            if (message instanceof ToolResultMessage resultMessage) {
                throw new IllegalArgumentException("存在无法关联的Tool Result ID=" + resultMessage.toolCallId());
            }

            AssistantToolCallsMessage assistantMessage = (AssistantToolCallsMessage) message;
            List<ToolCall> calls = assistantMessage.toolCalls();
            if (index + calls.size() >= messages.size()) {
                throw new IllegalArgumentException("Assistant Tool Calls存在遗漏的Tool Result");
            }

            for (int callIndex = 0; callIndex < calls.size(); callIndex++) {
                ToolCall call = calls.get(callIndex);
                ToolCallingMessage candidate = messages.get(index + callIndex + 1);
                if (!(candidate instanceof ToolResultMessage result)) {
                    throw new IllegalArgumentException("Tool Call缺少对应结果，ID=" + call.id());
                }
                if (!call.id().equals(result.toolCallId())) {
                    throw new IllegalArgumentException("Tool Call ID与Tool Result不匹配，expected=" + call.id());
                }
                if (!call.name().equals(result.toolName())) {
                    throw new IllegalArgumentException("Tool Call工具名与Tool Result不匹配，ID=" + call.id());
                }
            }
            index += calls.size();
        }

        if (!userMessageFound) throw new IllegalArgumentException("至少需要一条USER消息");
    }
}