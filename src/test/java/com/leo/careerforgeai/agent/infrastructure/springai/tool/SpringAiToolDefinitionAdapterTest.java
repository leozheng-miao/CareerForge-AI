package com.leo.careerforgeai.agent.infrastructure.springai.tool;

import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.agent.domain.tool.ToolRiskLevel;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.adapter.SpringAiToolDefinitionAdapter;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Spring AI工具定义完整复用公共Tool Contract语义。
 * @author: Miao Zheng
 * @date: 2026-08-07 17:50
 **/
class SpringAiToolDefinitionAdapterTest {

    private final SpringAiToolDefinitionAdapter adapter = new SpringAiToolDefinitionAdapter();

    @Test
    void shouldPreservePublicToolDefinitionExactly() {
        String inputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "query": {"type": "string", "maxLength": 500}
                  },
                  "required": ["query"],
                  "additionalProperties": false
                }
                """;
        ToolContract<TestInput, TestOutput> contract = new ToolContract<>(
                new ToolDefinition("search_career_materials", "搜索受控职业材料", inputSchema),
                "{\"type\":\"object\"}",
                TestInput.class,
                TestOutput.class,
                ToolImplementationType.RETRIEVAL_BACKED,
                ToolRiskLevel.LOW,
                true,
                1_000,
                10_000,
                10,
                Duration.ofSeconds(10)
        );

        org.springframework.ai.tool.definition.ToolDefinition result = adapter.adapt(contract);

        assertThat(result.name()).isEqualTo(contract.definition().name());
        assertThat(result.description()).isEqualTo(contract.definition().description());
        assertThat(result.inputSchema()).isEqualTo(contract.definition().inputSchemaJson());
    }

    @Test
    void shouldRejectMissingContract() {
        assertThatThrownBy(() -> adapter.adapt(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("contract不能为空");
    }

    private record TestInput(String query) {
    }

    private record TestOutput(String status) {
    }
}