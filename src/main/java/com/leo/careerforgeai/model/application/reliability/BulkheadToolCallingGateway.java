package com.leo.careerforgeai.model.application.reliability;

import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.application.reliability.ModelCallBulkhead;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 在真实DeepSeek Tool Calling边界应用模型调用并发舱壁
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
@Component
@Primary
public class BulkheadToolCallingGateway implements ToolCallingGateway {

    private final ToolCallingGateway delegate;
    private final ModelCallBulkhead bulkhead;

    public BulkheadToolCallingGateway(
            @Qualifier("deepSeekToolCallingClient") ToolCallingGateway delegate,
            ModelCallBulkhead bulkhead
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为空");
        this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead不能为空");
    }

    @Override
    public ToolCallingModelResult call(ToolCallingRequest request) {
        Objects.requireNonNull(request, "request不能为空");
        return bulkhead.execute(() -> delegate.call(request));
    }
}