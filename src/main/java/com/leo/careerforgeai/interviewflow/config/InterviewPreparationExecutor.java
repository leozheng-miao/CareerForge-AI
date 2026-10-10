package com.leo.careerforgeai.interviewflow.config;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/** 综合面试准备专用的有界虚拟线程入口，不与任务租约心跳共用。 */
@Component
public final class InterviewPreparationExecutor {
    private static final int GLOBAL_PERMITS = 6;
    private final Semaphore permits = new Semaphore(GLOBAL_PERMITS);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T> Future<T> submit(Callable<T> job) {
        Objects.requireNonNull(job, "job不能为空");
        return executor.submit(() -> {
            permits.acquire();
            try {
                return job.call();
            } finally {
                permits.release();
            }
        });
    }

    @PreDestroy
    void close() {
        executor.close();
    }
}
