package com.leo.careerforgeai.model.application.reliability;

import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 在有限重试层外统计一次完整Tool Calling逻辑调用并应用共享熔断器
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
public class CircuitBreakingToolCallingGateway implements ToolCallingGateway {

    private final ToolCallingGateway delegate;
    private final ModelCircuitBreaker circuitBreaker;

    public CircuitBreakingToolCallingGateway(
            @Qualifier("retryingToolCallingGateway") ToolCallingGateway delegate,
            ModelCircuitBreaker circuitBreaker
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为空");
        this.circuitBreaker = Objects.requireNonNull(
                circuitBreaker,
                "circuitBreaker不能为空"
        );
    }

    @Override
    public ToolCallingModelResult call(ToolCallingRequest request) {
        Objects.requireNonNull(request, "request不能为空");
        return circuitBreaker.execute(() -> delegate.call(request));
    }
}