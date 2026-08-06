package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.agent.domain.loop.AgentInputEstimate;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 定义 Agent 模型调用前的可替换 Token 软估算能力。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:31
 **/
public interface AgentTokenEstimator {

    /** 估算消息历史和工具定义组成的模型输入大小。 */
    AgentInputEstimate estimate(List<ToolCallingMessage> messages, List<ToolDefinition> tools);
}