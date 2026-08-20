package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.domain.loop.AgentInputEstimate;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopRequest;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.FinalAnswerResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * @program: CareerForge-AI
 * @description: 确定性验证同步Agent Loop调用线程会持续等待阻塞式模型边界返回
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class SynchronousAgentLoopBlockingBaselineTest {

    private static final int LOAD_REQUEST_COUNT = 40;
    private static final int LOAD_CONCURRENCY = 8;
    private static final Duration STUB_MODEL_LATENCY = Duration.ofMillis(200);

    private final ExecutorService requestExecutor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("cp0-sync-request-", 0).factory()
    );

    @AfterEach
    void closeResources() {
        requestExecutor.shutdownNow();
    }

    @Test
    void shouldKeepCallerBlockedUntilModelGatewayReturns() throws Exception {
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        ToolCallingGateway blockingGateway = request -> {
            modelEntered.countDown();
            try {
                releaseModel.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ModelException(
                        ModelErrorType.NETWORK_ERROR,
                        "Stub模型调用被中断",
                        exception
                );
            }
            return new FinalAnswerResult(
                    "cp0-request-1",
                    "cp0-blocking-stub",
                    "{\"status\":\"ANSWERED\",\"answer\":\"baseline\"}",
                    new ModelUsage(100, 20, 120)
            );
        };
        AgentLoop loop = createLoop(blockingGateway);
        Future<AgentLoopResult> future = requestExecutor.submit(
                () -> loop.run(createRequest())
        );

        try {
            assertThat(modelEntered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(future.isDone()).isFalse();
        } finally {
            releaseModel.countDown();
        }

        AgentLoopResult result = future.get(1, TimeUnit.SECONDS);
        assertThat(result.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.trace().modelCalls()).hasSize(1);
        assertThat(result.trace().toolCalls()).isEmpty();
    }
    @Test
    void shouldRecordFixedSynchronousPlatformThreadLoadBaseline() throws Exception {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger activeModelCalls = new AtomicInteger();
        AtomicInteger maxActiveModelCalls = new AtomicInteger();
        CyclicBarrier waveBarrier = new CyclicBarrier(LOAD_CONCURRENCY);

        ToolCallingGateway blockingGateway = request -> {
            modelCalls.incrementAndGet();
            int currentActive = activeModelCalls.incrementAndGet();
            maxActiveModelCalls.accumulateAndGet(currentActive, Math::max);

            try {
                awaitWave(waveBarrier);
                blockFor(STUB_MODEL_LATENCY);
                return new FinalAnswerResult(
                        "cp0-load-request-" + modelCalls.get(),
                        "cp0-blocking-stub",
                        "{\"status\":\"ANSWERED\",\"answer\":\"baseline\"}",
                        new ModelUsage(100, 20, 120)
                );
            } finally {
                activeModelCalls.decrementAndGet();
            }
        };

        AgentLoop loop = createLoop(blockingGateway);
        AgentLoopRequest request = createRequest();
        ExecutorService loadExecutor = Executors.newFixedThreadPool(
                LOAD_CONCURRENCY,
                Thread.ofPlatform().name("cp0-platform-request-", 0).factory()
        );
        List<Future<Long>> futures = new ArrayList<>(LOAD_REQUEST_COUNT);
        long batchStartedAt = System.nanoTime();

        try {
            for (int index = 0; index < LOAD_REQUEST_COUNT; index++) {
                long submittedAt = System.nanoTime();
                futures.add(loadExecutor.submit(() -> {
                    AgentLoopResult result = loop.run(request);
                    if (result.status() != AgentRunStatus.COMPLETED) {
                        throw new IllegalStateException(
                                "同步基线请求未完成，status=" + result.status()
                        );
                    }
                    return TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - submittedAt
                    );
                }));
            }

            List<Long> latenciesMs = new ArrayList<>(LOAD_REQUEST_COUNT);
            for (Future<Long> future : futures) {
                latenciesMs.add(future.get(15, TimeUnit.SECONDS));
            }

            long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - batchStartedAt
            );
            Collections.sort(latenciesMs);

            double durationSeconds = Math.max(
                    totalDurationMs / 1_000.0,
                    0.001
            );
            double throughputRps = LOAD_REQUEST_COUNT / durationSeconds;

            System.out.printf(
                    Locale.ROOT,
                    "CP0_SYNC_BASELINE requestCount=%d concurrency=%d "
                            + "userMessageChars=512 modelLatencyMs=%d "
                            + "toolLatencyMs=0 failureInjection=NONE "
                            + "completed=%d maxActiveModelCalls=%d "
                            + "totalDurationMs=%d throughputRps=%.2f "
                            + "p50Ms=%d p95Ms=%d%n",
                    LOAD_REQUEST_COUNT,
                    LOAD_CONCURRENCY,
                    STUB_MODEL_LATENCY.toMillis(),
                    latenciesMs.size(),
                    maxActiveModelCalls.get(),
                    totalDurationMs,
                    throughputRps,
                    percentile(latenciesMs, 0.50),
                    percentile(latenciesMs, 0.95)
            );

            assertThat(modelCalls).hasValue(LOAD_REQUEST_COUNT);
            assertThat(latenciesMs).hasSize(LOAD_REQUEST_COUNT);
            assertThat(maxActiveModelCalls).hasValue(LOAD_CONCURRENCY);
        } finally {
            loadExecutor.shutdownNow();
            loadExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void awaitWave(CyclicBarrier waveBarrier) {
        try {
            waveBarrier.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelException(
                    ModelErrorType.NETWORK_ERROR,
                    "同步基线等待被中断",
                    exception
            );
        } catch (BrokenBarrierException | TimeoutException exception) {
            throw new ModelException(
                    ModelErrorType.NETWORK_ERROR,
                    "同步基线并发波次未能汇合",
                    exception
            );
        }
    }

    private void blockFor(Duration duration) {
        long deadline = System.nanoTime() + duration.toNanos();
        long remaining;

        while ((remaining = deadline - System.nanoTime()) > 0) {
            LockSupport.parkNanos(remaining);
            if (Thread.currentThread().isInterrupted()) {
                throw new ModelException(
                        ModelErrorType.NETWORK_ERROR,
                        "Stub模型阻塞被中断"
                );
            }
        }
    }

    private long percentile(List<Long> sortedValues, double percentile) {
        int index = (int) Math.ceil(sortedValues.size() * percentile) - 1;
        return sortedValues.get(Math.max(index, 0));
    }

    private AgentLoop createLoop(ToolCallingGateway gateway) {
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        AgentTokenEstimator tokenEstimator = mock(AgentTokenEstimator.class);

        when(toolRegistry.definitions()).thenReturn(List.of(
                new ToolDefinition(
                        "cp0_baseline_tool",
                        "仅用于同步基线的模型可见工具",
                        """
                        {
                          "type":"object",
                          "properties":{},
                          "additionalProperties":false
                        }
                        """
                )
        ));
        when(tokenEstimator.estimate(anyList(), anyList()))
                .thenReturn(new AgentInputEstimate(100, 256));

        AgentLoopPolicy policy = new AgentLoopPolicy(
                6,
                8,
                4,
                2,
                20_000,
                2_000,
                80_000,
                Duration.ofSeconds(60),
                Duration.ofSeconds(30)
        );

        return new AgentLoop(
                gateway,
                toolRegistry,
                mock(SafeToolExecutor.class),
                tokenEstimator,
                mock(ToolCallFingerprintService.class),
                policy,
                Clock.systemUTC()
        );
    }

    private AgentLoopRequest createRequest() {
        return new AgentLoopRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "CP0同步阻塞基线"),
                        new ToolCallingTextMessage(ModelRole.USER, "x".repeat(512))
                ),
                new RetrievalScope("cp0-baseline", Set.of(), Set.of()),
                ModelOutputFormat.JSON_OBJECT,
                "cp0-sync-baseline-v1"
        );
    }
}