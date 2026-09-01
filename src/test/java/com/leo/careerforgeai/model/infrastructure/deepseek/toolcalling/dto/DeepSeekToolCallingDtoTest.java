package com.leo.careerforgeai.model.infrastructure.deepseek.toolcalling.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证DeepSeek Tool Calling请求和响应DTO的JSON协议映射。
 * @author: Miao Zheng
 * @date: 2026-08-07 14:40
 **/
class DeepSeekToolCallingDtoTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void shouldSerializeToolCallingMessagesSchemaAndResponseFormat() throws Exception {
        JsonNode schema = jsonMapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "query": {"type": "string"}
                  },
                  "required": ["query"],
                  "additionalProperties": false
                }
                """);

        DeepSeekToolCallingRequest request = new DeepSeekToolCallingRequest(
                "deepseek-v4-flash",
                List.of(
                        new DeepSeekToolCallingRequest.Message("system", "系统规则", null, null),
                        new DeepSeekToolCallingRequest.Message("user", "查询材料", null, null),
                        new DeepSeekToolCallingRequest.Message(
                                "assistant",
                                "",
                                List.of(new DeepSeekToolCallingRequest.ToolCall(
                                        "call-1",
                                        "function",
                                        new DeepSeekToolCallingRequest.FunctionCall(
                                                "search_career_materials",
                                                "{\"query\":\"Java并发\"}"
                                        )
                                )),
                                null
                        ),
                        new DeepSeekToolCallingRequest.Message(
                                "tool",
                                "{\"status\":\"SUCCESS\"}",
                                null,
                                "call-1"
                        )
                ),
                List.of(new DeepSeekToolCallingRequest.Tool(
                        "function",
                        new DeepSeekToolCallingRequest.FunctionDefinition(
                                "search_career_materials",
                                "检索职业材料证据",
                                false,
                                schema
                        )
                )),
                "auto",
                new DeepSeekToolCallingRequest.Thinking("disabled"),
                512,
                1.0,
                false,
                new DeepSeekToolCallingRequest.ResponseFormat("json_object")
        );

        JsonNode root = jsonMapper.readTree(jsonMapper.writeValueAsString(request));

        assertThat(root.get("tool_choice").asText()).isEqualTo("auto");
        assertThat(root.get("thinking").get("type").asText()).isEqualTo("disabled");
        assertThat(root.get("response_format").get("type").asText()).isEqualTo("json_object");
        assertThat(root.get("max_tokens").asInt()).isEqualTo(512);
        assertThat(root.get("temperature").asDouble()).isEqualTo(1.0);
        assertThat(root.get("messages").get(2).get("tool_calls").get(0).get("id").asText()).isEqualTo("call-1");
        assertThat(root.get("messages").get(3).get("tool_call_id").asText()).isEqualTo("call-1");
        assertThat(root.get("messages").get(0).get("tool_calls")).isNull();
        assertThat(root.get("tools").get(0).get("function").get("parameters").isObject()).isTrue();
    }

    @Test
    void shouldDeserializeRealToolCallsResponseShape() throws Exception {
        String responseJson = """
                {
                  "id": "request-1",
                  "model": "deepseek-v4-flash",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "",
                      "tool_calls": [{
                        "index": 0,
                        "id": "call-1",
                        "type": "function",
                        "function": {
                          "name": "search_career_materials",
                          "arguments": "{\\"query\\":\\"Java并发\\"}"
                        }
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }],
                  "usage": {
                    "prompt_tokens": 486,
                    "completion_tokens": 69,
                    "total_tokens": 555,
                    "prompt_tokens_details": {"cached_tokens": 0}
                  },
                  "system_fingerprint": "ignored"
                }
                """;

        DeepSeekToolCallingResponse response = jsonMapper.readValue(responseJson, DeepSeekToolCallingResponse.class);

        DeepSeekToolCallingResponse.Choice choice = response.choices().getFirst();
        DeepSeekToolCallingResponse.ToolCall call = choice.message().toolCalls().getFirst();

        assertThat(response.model()).isEqualTo("deepseek-v4-flash");
        assertThat(choice.finishReason()).isEqualTo("tool_calls");
        assertThat(choice.message().content()).isEmpty();
        assertThat(call.id()).isEqualTo("call-1");
        assertThat(call.function().name()).isEqualTo("search_career_materials");
        assertThat(call.function().arguments()).isEqualTo("{\"query\":\"Java并发\"}");
        assertThat(response.usage().totalTokens()).isEqualTo(555);
    }

    @Test
    void shouldDeserializeFinalAnswerWithoutToolCalls() throws Exception {
        String responseJson = """
                {
                  "id": "request-2",
                  "model": "deepseek-v4-flash",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "最终回答"
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 100,
                    "completion_tokens": 20,
                    "total_tokens": 120
                  }
                }
                """;

        DeepSeekToolCallingResponse response = jsonMapper.readValue(responseJson, DeepSeekToolCallingResponse.class);

        assertThat(response.choices().getFirst().finishReason()).isEqualTo("stop");
        assertThat(response.choices().getFirst().message().content()).isEqualTo("最终回答");
        assertThat(response.choices().getFirst().message().toolCalls()).isNull();
    }
}