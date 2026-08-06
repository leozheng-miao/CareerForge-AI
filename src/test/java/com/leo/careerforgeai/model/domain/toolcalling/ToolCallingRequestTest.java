package com.leo.careerforgeai.model.domain.toolcalling;

import com.leo.careerforgeai.model.domain.ModelRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-06 15:37
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
                512
        );

        assertThat(request.messages()).hasSize(2);
        assertThat(request.tools()).containsExactly(SEARCH_TOOL);
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
                512
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复工具名称");
    }
}