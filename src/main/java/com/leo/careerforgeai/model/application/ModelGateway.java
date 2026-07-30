package com.leo.careerforgeai.model.application;

import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;

import java.util.function.Consumer;

public interface ModelGateway {

    ModelResponse chat(ModelRequest request);
    void stream(ModelRequest request, Consumer<ModelStreamEvent> eventConsumer);
}