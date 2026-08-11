package com.leo.careerforgeai.agent.evaluation.execution;

import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallsResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 记录模型实际请求的全部Tool Call，包括尚未执行就被Agent Loop拒绝的调用。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public final class RecordingToolCallingGateway implements ToolCallingGateway {

    private final ToolCallingGateway delegate;
    private final List<RecordedToolCall> recordedToolCalls = new ArrayList<>();
    private int modelIteration;
    private int toolSequence;

    public RecordingToolCallingGateway(ToolCallingGateway delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为空");
    }

    @Override
    public synchronized ToolCallingModelResult call(ToolCallingRequest request) {
        modelIteration++;
        ToolCallingModelResult result = delegate.call(request);

        if (result instanceof ToolCallsResult toolCallsResult) {
            for (ToolCall toolCall : toolCallsResult.toolCalls()) {
                toolSequence++;
                recordedToolCalls.add(new RecordedToolCall(
                        toolSequence,
                        modelIteration,
                        toolCall.id(),
                        toolCall.name(),
                        toolCall.argumentsJson()
                ));
            }
        }
        return result;
    }

    public synchronized List<RecordedToolCall> recordedToolCalls() {
        return List.copyOf(recordedToolCalls);
    }

    public record RecordedToolCall(
            int sequence,
            int modelIteration,
            String toolCallId,
            String toolName,
            String argumentsJson
    ) {

        public RecordedToolCall {
            if (sequence <= 0) throw new IllegalArgumentException("sequence必须大于0");
            if (modelIteration <= 0) throw new IllegalArgumentException("modelIteration必须大于0");
            requireText(toolCallId, "toolCallId");
            requireText(toolName, "toolName");
            requireText(argumentsJson, "argumentsJson");
        }

        private static void requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }
}