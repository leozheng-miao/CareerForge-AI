package com.leo.careerforgeai.model.application.reliability;

import com.leo.careerforgeai.model.config.ModelCallBulkheadProperties;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * @program: CareerForge-AI
 * @description: 使用公平Semaphore对单JVM在途模型调用执行零等待舱壁控制
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
@Component
public class ModelCallBulkhead {

    private final Semaphore permits;

    public ModelCallBulkhead(ModelCallBulkheadProperties properties) {
        Objects.requireNonNull(properties, "properties不能为空");
        this.permits = new Semaphore(properties.maxConcurrentCalls(), true);
    }

    public <T> T execute(Supplier<T> action) {
        Objects.requireNonNull(action, "action不能为空");
        if (!permits.tryAcquire()) {
            throw new ModelException(
                    ModelErrorType.CAPACITY_REJECTED,
                    "模型调用并发容量已满"
            );
        }

        try {
            return action.get();
        } finally {
            permits.release();
        }
    }
}