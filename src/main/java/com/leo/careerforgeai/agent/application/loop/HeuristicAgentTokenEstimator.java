package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.agent.domain.loop.AgentInputEstimate;
import com.leo.careerforgeai.model.domain.toolcalling.AssistantToolCallsMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import com.leo.careerforgeai.model.domain.toolcalling.ToolResultMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 使用字符启发式和安全余量估算 Tool Calling 请求的输入 Token。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:31
 **/
public final class HeuristicAgentTokenEstimator implements AgentTokenEstimator {

    private static final int MESSAGE_TOKEN_OVERHEAD = 8;
    private static final int TOOL_CALL_TOKEN_OVERHEAD = 12;
    private static final int TOOL_DEFINITION_TOKEN_OVERHEAD = 16;
    private static final int MESSAGE_CHAR_OVERHEAD = 32;
    private static final int TOOL_CALL_CHAR_OVERHEAD = 48;
    private static final int TOOL_DEFINITION_CHAR_OVERHEAD = 64;

    /** 统计全部模型可见文本，并加入协议开销和百分之二十安全余量。 */
    @Override
    public AgentInputEstimate estimate(List<ToolCallingMessage> messages, List<ToolDefinition> tools) {
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("messages 不能为空");
        if (messages.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("messages 不能包含 null");
        if (tools == null || tools.isEmpty()) throw new IllegalArgumentException("tools 不能为空");
        if (tools.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("tools 不能包含 null");

        List<String> visibleParts = new ArrayList<>();
        long estimatedTokens = 0;
        long visibleChars = 0;

        for (ToolCallingMessage message : messages) {
            estimatedTokens = safeAdd(estimatedTokens, MESSAGE_TOKEN_OVERHEAD);
            visibleChars = safeAdd(visibleChars, MESSAGE_CHAR_OVERHEAD);

            if (message instanceof ToolCallingTextMessage textMessage) {
                visibleParts.add(textMessage.role().name());
                visibleParts.add(textMessage.content());
                continue;
            }

            if (message instanceof AssistantToolCallsMessage assistantMessage) {
                visibleParts.add("assistant");
                for (ToolCall toolCall : assistantMessage.toolCalls()) {
                    estimatedTokens = safeAdd(estimatedTokens, TOOL_CALL_TOKEN_OVERHEAD);
                    visibleChars = safeAdd(visibleChars, TOOL_CALL_CHAR_OVERHEAD);
                    visibleParts.add(toolCall.id());
                    visibleParts.add(toolCall.name());
                    visibleParts.add(toolCall.argumentsJson());
                }
                continue;
            }

            ToolResultMessage resultMessage = (ToolResultMessage) message;
            visibleParts.add("tool");
            visibleParts.add(resultMessage.toolCallId());
            visibleParts.add(resultMessage.toolName());
            visibleParts.add(resultMessage.content());
        }

        for (ToolDefinition tool : tools) {
            estimatedTokens = safeAdd(estimatedTokens, TOOL_DEFINITION_TOKEN_OVERHEAD);
            visibleChars = safeAdd(visibleChars, TOOL_DEFINITION_CHAR_OVERHEAD);
            visibleParts.add(tool.name());
            visibleParts.add(tool.description());
            visibleParts.add(tool.inputSchemaJson());
        }

        for (String part : visibleParts) {
            estimatedTokens = safeAdd(estimatedTokens, estimateTextTokens(part));
            visibleChars = safeAdd(visibleChars, part.length());
        }

        estimatedTokens = safeAdd(estimatedTokens, ceilDivide(estimatedTokens, 5));
        int boundedChars = visibleChars >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) visibleChars;
        return new AgentInputEstimate(estimatedTokens, boundedChars);
    }

    /** 将非 ASCII 字符按每字符一个 Token、ASCII 按每三个字符一个 Token 进行保守估算。 */
    private long estimateTextTokens(String text) {
        long asciiCharacters = 0;
        long nonAsciiTokens = 0;

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (codePoint <= 0x7F) asciiCharacters++;
            else nonAsciiTokens++;
            offset += Character.charCount(codePoint);
        }

        return safeAdd(nonAsciiTokens, ceilDivide(asciiCharacters, 3));
    }

    /** 执行不会因加法预处理而溢出的向上整除。 */
    private long ceilDivide(long value, long divisor) {
        if (value == 0) return 0;
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    /** 使用饱和加法防止异常超长输入导致 long 回绕。 */
    private long safeAdd(long current, long value) {
        try {
            return Math.addExact(current, value);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}