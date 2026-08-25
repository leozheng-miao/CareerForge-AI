package com.leo.careerforgeai.agent.infrastructure.observability;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @program: CareerForge-AI
 * @description: 在性能Profile中定时采集Hikari连接池使用量和等待量
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
@Component
@Profile("performance-stub")
@Slf4j
public final class PerformanceHikariMetricsLogger {

    private final HikariDataSource dataSource;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger peakActive = new AtomicInteger();
    private final AtomicInteger peakPending = new AtomicInteger();

    public PerformanceHikariMetricsLogger(HikariDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource不能为空");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("careerforge-hikari-metrics-", 0).factory()
        );
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::sample, 1, 1, TimeUnit.SECONDS);
    }

    private void sample() {
        try {
            HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
            if (pool == null) return;

            int active = pool.getActiveConnections();
            int pending = pool.getThreadsAwaitingConnection();
            peakActive.accumulateAndGet(active, Math::max);
            peakPending.accumulateAndGet(pending, Math::max);
            log.info(
                    "Hikari压测指标，active={}, idle={}, total={}, pending={}",
                    active,
                    pool.getIdleConnections(),
                    pool.getTotalConnections(),
                    pending
            );
        } catch (RuntimeException exception) {
            log.warn("Hikari压测指标采集失败，errorType={}", exception.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public void close() {
        scheduler.shutdownNow();
        log.info("Hikari压测峰值，peakActive={}, peakPending={}", peakActive.get(), peakPending.get());
    }
}