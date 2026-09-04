package com.leo.careerforgeai.model.application.routing;

import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.config.ModelTaskPolicyProperties;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @program: CareerForge-AI
 * @description: 根据后端任务策略统一模型请求参数并交给路由执行边界
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@Component
public final class RoutingModelGateway implements ModelGateway {
    private final ModelCallExecutor executor;
    private final ModelTaskPolicyProperties taskPolicies;

    public RoutingModelGateway(ModelCallExecutor executor, ModelTaskPolicyProperties taskPolicies) {
        this.executor = Objects.requireNonNull(executor, "executor不能为空");
        this.taskPolicies = Objects.requireNonNull(taskPolicies, "taskPolicies不能为空");
    }

    @Override
    public ModelResponse chat(ModelTaskType taskType, ModelRequest request) {
        ModelRequest effectiveRequest = taskPolicies.apply(taskType, request);
        boolean fallbackAllowed = taskPolicies.require(taskType).fallbackAllowed();
        return executor.chat(taskType, Set.of(), effectiveRequest,
                effectiveRequest.maxOutputTokens(), fallbackAllowed);
    }

    @Override
    public void stream(ModelTaskType taskType, ModelRequest request,
                       Consumer<ModelStreamEvent> eventConsumer) {
        ModelRequest effectiveRequest = taskPolicies.apply(taskType, request);
        executor.stream(taskType, Set.of(), effectiveRequest,
                effectiveRequest.maxOutputTokens(), eventConsumer);
    }
}