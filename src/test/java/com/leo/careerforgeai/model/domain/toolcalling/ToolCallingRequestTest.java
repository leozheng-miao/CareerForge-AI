package com.leo.careerforgeai.model.domain.toolcalling;

import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Tool Calling请求格式、消息关联和协议级输入边界。
 * @author: Miao Zheng
 * @date: 2026-08-07 14:40
 **/
class ToolCallingRequestTest {

    private static final ToolDefinition SEARCH_TOOL = new ToolDefinition(
            "search_career_materials",
            "检索职业材料证据",
            "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}"
    );

    @Test
    void shouldCreateInitialRequestWithoutToolHistory() {
        ToolCallingRequest request = new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "系统规则"),
                        new ToolCallingTextMessage(ModelRole.USER, "查询职业材料")
                ),
                List.of(SEARCH_TOOL),
                ToolChoiceMode.AUTO,
                ModelOutputFormat.JSON_OBJECT,
                512
        );

        assertThat(request.messages()).hasSize(2);
        assertThat(request.tools()).containsExactly(SEARCH_TOOL);
        assertThat(request.outputFormat()).isEqualTo(ModelOutputFormat.JSON_OBJECT);
    }

    @Test
    void shouldAcceptOrderedToolCallsAndResults() {
        ToolCall first = new ToolCall("call-1", "search_career_materials", "{\"query\":\"Java并发\"}");
        ToolCall second = new ToolCall("call-2", "unknown_tool", "{}");

        ToolCallingRequest request = new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "系统规则"),
                        new ToolCallingTextMessage(ModelRole.USER, "查询职业材料"),
                        new AssistantToolCallsMessage(List.of(first, second)),
                        new ToolResultMessage("call-1", "search_career_materials", "{\"status\":\"SUCCESS\"}"),
                        new ToolResultMessage("call-2", "unknown_tool", "{\"status\":\"UNKNOWN_TOOL\"}")
                ),
                List.of(SEARCH_TOOL),
                ToolChoiceMode.AUTO,
                ModelOutputFormat.JSON_OBJECT,
                512
        );

        assertThat(request.messages()).hasSize(5);
    }

    @Test
    void shouldRejectMissingOrMismatchedToolResult() {
        ToolCall first = new ToolCall("call-1", "search_career_materials", "{}");
        ToolCall second = new ToolCall("call-2", "search_career_materials", "{}");

        assertThatThrownBy(() -> new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "系统规则"),
                        new ToolCallingTextMessage(ModelRole.USER, "查询职业材料"),
                        new AssistantToolCallsMessage(List.of(first, second)),
                        new ToolResultMessage("call-1", "search_career_materials", "{}")
                ),
                List.of(SEARCH_TOOL),
                ToolChoiceMode.AUTO,
                ModelOutputFormat.JSON_OBJECT,
                512
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("遗漏");

        assertThatThrownBy(() -> new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "系统规则"),
                        new ToolCallingTextMessage(ModelRole.USER, "查询职业材料"),
                        new AssistantToolCallsMessage(List.of(first)),
                        new ToolResultMessage("wrong-id", "search_career_materials", "{}")
                ),
                List.of(SEARCH_TOOL),
                ToolChoiceMode.AUTO,
                ModelOutputFormat.JSON_OBJECT,
                512
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    void shouldRejectDuplicateToolDefinitions() {
        assertThatThrownBy(() -> new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "系统规则"),
                        new ToolCallingTextMessage(ModelRole.USER, "查询职业材料")
                ),
                List.of(SEARCH_TOOL, SEARCH_TOOL),
                ToolChoiceMode.AUTO,
                ModelOutputFormat.JSON_OBJECT,
                512
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复工具名称");
    }

    @Test
    void shouldRejectMissingOutputFormat() {
        assertThatThrownBy(() -> new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "系统规则"),
                        new ToolCallingTextMessage(ModelRole.USER, "查询职业材料")
                ),
                List.of(SEARCH_TOOL),
                ToolChoiceMode.AUTO,
                null,
                512
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputFormat");
    }

    @Test
    void shouldRejectOversizedToolCallFields() {
        assertThatThrownBy(() -> new ToolCall("a".repeat(129), "search_career_materials", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID超过长度限制");

        assertThatThrownBy(() -> new ToolCall("call-1", "a".repeat(65), "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("名称超过长度限制");

        assertThatThrownBy(() -> new ToolCall("call-1", "search_career_materials", "a".repeat(30_001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("协议级长度限制");
    }
}