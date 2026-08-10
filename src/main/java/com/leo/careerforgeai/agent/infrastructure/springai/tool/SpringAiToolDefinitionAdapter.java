package com.leo.careerforgeai.agent.infrastructure.springai.tool;

import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 将项目公共Tool Contract无损转换为Spring AI工具定义。
 * @author: Miao Zheng
 * @date: 2026-08-07 17:50
 **/
public final class SpringAiToolDefinitionAdapter {

    /** 复用公共契约中的名称、描述和输入Schema，不重新生成另一套工具语义。 */
    public org.springframework.ai.tool.definition.ToolDefinition adapt(ToolContract<?, ?> contract) {
        Objects.requireNonNull(contract, "contract不能为空");
        var definition = contract.definition();
        return DefaultToolDefinition.builder()
                .name(definition.name())
                .description(definition.description())
                .inputSchema(definition.inputSchemaJson())
                .build();
    }
}