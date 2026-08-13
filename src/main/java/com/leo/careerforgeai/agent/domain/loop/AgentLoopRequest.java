package com.leo.careerforgeai.agent.domain.loop;

import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存由服务端构造的 Agent 初始消息、检索权限范围和上下文版本。
 * @author: Miao Zheng
 * @date: 2026-08-06 18:00
 **/
public record AgentLoopRequest(
        List<ToolCallingTextMessage> initialMessages,
        RetrievalScope retrievalScope,
        ModelOutputFormat outputFormat,
        String contextVersion
) {

    public AgentLoopRequest {
        if (initialMessages == null || initialMessages.isEmpty()) {
            throw new IllegalArgumentException("initialMessages 不能为空");
        }
        if (initialMessages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("initialMessages 不能包含 null");
        }
        if (initialMessages.getFirst().role() != ModelRole.SYSTEM) {
            throw new IllegalArgumentException("第一条初始消息必须是 SYSTEM");
        }
        if (initialMessages.stream().skip(1).anyMatch(message -> message.role() == ModelRole.SYSTEM)) {
            throw new IllegalArgumentException("SYSTEM 消息只能位于第一条");
        }
        if (initialMessages.stream().noneMatch(message -> message.role() == ModelRole.USER)) {
            throw new IllegalArgumentException("至少需要一条 USER 消息");
        }
        if (initialMessages.getLast().role() != ModelRole.USER) {
            throw new IllegalArgumentException("最后一条初始消息必须是当前USER消息");
        }

        Objects.requireNonNull(retrievalScope, "retrievalScope 不能为空");
        Objects.requireNonNull(outputFormat, "outputFormat 不能为空");
        if (contextVersion == null || contextVersion.isBlank()) {
            throw new IllegalArgumentException("contextVersion 不能为空");
        }
        if (contextVersion.length() > 128) {
            throw new IllegalArgumentException("contextVersion 不能超过 128 个字符");
        }

        initialMessages = List.copyOf(initialMessages);
    }
}