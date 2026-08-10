package com.leo.careerforgeai.agent.infrastructure.springai.tool;

import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 将Java工具白名单转换为Spring AI使用的不可变ToolCallback列表。
 * @author: Miao Zheng
 * @date: 2026-08-07 20:10
 **/
public final class SpringAiToolCallbackCatalog {

    private final List<ToolCallback> callbacks;

    public SpringAiToolCallbackCatalog(ToolRegistry registry,
                                       SpringAiToolDefinitionAdapter definitionAdapter,
                                       SafeToolExecutor safeToolExecutor) {
        Objects.requireNonNull(registry, "registry不能为空");
        Objects.requireNonNull(definitionAdapter, "definitionAdapter不能为空");
        Objects.requireNonNull(safeToolExecutor, "safeToolExecutor不能为空");
        this.callbacks = registry.contracts().stream()
                .<ToolCallback>map(contract -> new SpringAiToolCallbackAdapter(
                        contract, definitionAdapter, safeToolExecutor))
                .toList();
    }

    public List<ToolCallback> callbacks() {
        return callbacks;
    }
}