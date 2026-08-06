package com.leo.careerforgeai.agent.application.tool;

import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolRiskLevel;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 通过显式注册表限制 Agent 只能调用批准的只读工具。 */
public final class ToolRegistry {

    private final Map<String, AgentTool<?, ?>> toolsByName;

    public ToolRegistry(List<AgentTool<?, ?>> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            throw new IllegalArgumentException("allowedTools 不能为空");
        }

        Map<String, AgentTool<?, ?>> registeredTools = new LinkedHashMap<>();
        for (AgentTool<?, ?> tool : allowedTools) {
            if (tool == null) {
                throw new IllegalArgumentException("allowedTools 不能包含 null");
            }

            ToolContract<?, ?> contract = tool.contract();
            if (contract == null) {
                throw new IllegalArgumentException("工具 contract 不能为空");
            }
            if (!contract.readOnly()) {
                throw new IllegalArgumentException("阶段三只允许注册只读工具，toolName=" + contract.name());
            }
            if (contract.riskLevel() == ToolRiskLevel.HIGH) {
                throw new IllegalArgumentException("阶段三不允许注册高风险工具，toolName=" + contract.name());
            }
            if (registeredTools.putIfAbsent(contract.name(), tool) != null) {
                throw new IllegalArgumentException("存在重复工具名称=" + contract.name());
            }
        }

        this.toolsByName = Collections.unmodifiableMap(registeredTools);
    }

    public Optional<AgentTool<?, ?>> find(String toolName) {
        if (toolName == null || toolName.isBlank()) return Optional.empty();
        return Optional.ofNullable(toolsByName.get(toolName));
    }

    public List<ToolContract<?, ?>> contracts() {
        List<ToolContract<?, ?>> contracts = new ArrayList<>(toolsByName.size());
        for (AgentTool<?, ?> tool : toolsByName.values()) {
            contracts.add(tool.contract());
        }
        return List.copyOf(contracts);
    }

    public List<ToolDefinition> definitions() {
        return contracts().stream()
                .map(ToolContract::definition)
                .toList();
    }
}