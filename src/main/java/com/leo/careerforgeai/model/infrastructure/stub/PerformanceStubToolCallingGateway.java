package com.leo.careerforgeai.model.infrastructure.stub;

import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.config.PerformanceStubModelProperties;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.FinalAnswerResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.shared.exception.BusinessException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @program: CareerForge-AI
 * @description: 在性能Profile中模拟固定延迟模型调用并返回稳定合法的结构化回答
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
@Component("deepSeekToolCallingClient")
@Profile("performance-stub")
public final class PerformanceStubToolCallingGateway implements ToolCallingGateway {

    private static final String MODEL_NAME = "careerforge-performance-stub";
    private static final String FIXED_CONTENT = """
            {"status":"ANSWERED","answer":"性能压测固定回答","citedChunkIds":[]}
            """.strip();
    private static final ModelUsage FIXED_USAGE = new ModelUsage(80, 20, 100);

    private final PerformanceStubModelProperties properties;
    private final AtomicLong requestSequence = new AtomicLong();

    public PerformanceStubToolCallingGateway(PerformanceStubModelProperties properties) {
        this.properties = properties;
    }

    @Override
    public ToolCallingModelResult call(ToolCallingRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "Tool Calling 请求不能为空");

        try {
            if (!properties.latency().isZero()) Thread.sleep(properties.latency());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "性能Stub模型调用被中断", exception);
        }

        return new FinalAnswerResult(
                "performance-stub-" + requestSequence.incrementAndGet(),
                MODEL_NAME,
                FIXED_CONTENT,
                FIXED_USAGE
        );
    }
}