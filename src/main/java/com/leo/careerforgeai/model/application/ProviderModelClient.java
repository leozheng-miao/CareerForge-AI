package com.leo.careerforgeai.model.application;

import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;

import java.util.function.Consumer;

/**
 * @program: CareerForge-AI
 * @description: 定义统一模型协议与具体供应商协议之间的适配端口。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
public interface ProviderModelClient {

    String providerId();

    ModelResponse chat(ModelExecutionProfile profile, ModelRequest request);

    void stream(ModelExecutionProfile profile, ModelRequest request,
                Consumer<ModelStreamEvent> eventConsumer);
}