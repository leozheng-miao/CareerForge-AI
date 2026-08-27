package com.leo.careerforgeai.model.application.reliability;

import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.config.ModelReliabilityProperties;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 对Tool Calling瞬时故障执行受次数、退避、Retry-After和总超时约束的有限重试
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
@Component
public class RetryingToolCallingGateway implements ToolCallingGateway {

    private final ToolCallingGateway delegate;
    private final ModelRetryExecutor retryExecutor;

    public RetryingToolCallingGateway(
            @Qualifier("bulkheadToolCallingGateway") ToolCallingGateway delegate,
            ModelReliabilityProperties properties,
            ModelReliabilityMetrics metrics
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为空");
        this.retryExecutor = new ModelRetryExecutor(properties, metrics);
    }

    @Override
    public ToolCallingModelResult call(ToolCallingRequest request) {
        Objects.requireNonNull(request, "request不能为空");
        return retryExecutor.execute(
                request.timeout(),
                remainingTimeout -> delegate.call(request.withTimeout(remainingTimeout))
        );
    }
}