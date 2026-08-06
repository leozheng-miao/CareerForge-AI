package com.leo.careerforgeai.agent.application.tool;

import com.leo.careerforgeai.agent.domain.tool.AgentToolOutput;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;

/**
 * @program: CareerForge-AI
 * @description: 定义受控业务工具的公共契约和执行入口。
 * @author: Miao Zheng
 * @date: 2026-08-06 18:03
 **/
public interface AgentTool<I, O> {

    /** 返回供模型适配器、Registry 和执行器共同使用的工具契约。 */
    ToolContract<I, O> contract();

    /** 使用已校验输入和服务端上下文执行工具并返回业务数据及 Trace 元数据。 */
    AgentToolOutput<O> execute(I input, ToolExecutionContext context);
}