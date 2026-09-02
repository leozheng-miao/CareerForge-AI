package com.leo.careerforgeai.model.application.routing;

import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Consumer;

/**
 * @program: CareerForge-AI
 * @description: 将Java业务模型请求交给统一路由和执行边界。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@Component
public final class RoutingModelGateway implements ModelGateway {

    private final ModelCallExecutor executor;

    public RoutingModelGateway(ModelCallExecutor executor) {
        this.executor = java.util.Objects.requireNonNull(executor, "executor不能为空");
    }

    @Override
    public ModelResponse chat(ModelTaskType taskType, ModelRequest request) {
        return executor.chat(taskType, Set.of(), request,
                request.maxOutputTokens(), true);
    }

    @Override
    public void stream(ModelTaskType taskType, ModelRequest request,
                       Consumer<ModelStreamEvent> eventConsumer) {
        executor.stream(taskType, Set.of(), request,
                request.maxOutputTokens(), eventConsumer);
    }
}