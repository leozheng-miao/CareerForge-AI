package com.leo.careerforgeai.model.infrastructure.reliability;

import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.application.reliability.BulkheadToolCallingGateway;
import com.leo.careerforgeai.model.application.reliability.ModelCallBulkhead;
import com.leo.careerforgeai.model.config.ModelCallBulkheadProperties;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.FinalAnswerResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证模型舱壁并发拒绝、异常释放和Tool Calling委托边界
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
class BulkheadToolCallingGatewayTest {

    @Test
    void shouldRejectConcurrentCallAndReleasePermitAfterCompletion() throws Exception {
        ModelCallBulkhead bulkhead = new ModelCallBulkhead(
                new ModelCallBulkheadProperties(1)
        );
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = executor.submit(() -> bulkhead.execute(() -> {
                entered.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("等待释放模型调用超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("模型调用测试被中断", exception);
                }
                return "completed";
            }));

            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> bulkhead.execute(() -> "rejected"))
                    .isInstanceOfSatisfying(ModelException.class, exception ->
                            assertThat(exception.getErrorType())
                                    .isEqualTo(ModelErrorType.CAPACITY_REJECTED)
                    );

            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("completed");
            assertThat(bulkhead.execute(() -> "next")).isEqualTo("next");
        }
    }

    @Test
    void shouldIsolateConcurrentCapacityByProvider() throws Exception {
        ModelCallBulkhead bulkhead = new ModelCallBulkhead(
                new ModelCallBulkheadProperties(1));
        CountDownLatch deepseekEntered = new CountDownLatch(1);
        CountDownLatch releaseDeepseek = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> heldCall = executor.submit(() ->
                    bulkhead.execute("deepseek", () -> {
                        deepseekEntered.countDown();
                        try {
                            if (!releaseDeepseek.await(2, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("等待释放DeepSeek调用超时");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(
                                    "DeepSeek调用测试被中断", exception);
                        }
                        return "deepseek-completed";
                    }));

            assertThat(deepseekEntered.await(2, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() ->
                        bulkhead.execute("deepseek", () -> "rejected"))
                        .isInstanceOfSatisfying(ModelException.class, exception ->
                                assertThat(exception.getErrorType())
                                        .isEqualTo(ModelErrorType.CAPACITY_REJECTED));

                assertThat(bulkhead.execute("kimi", () -> "kimi-completed"))
                        .isEqualTo("kimi-completed");
            } finally {
                releaseDeepseek.countDown();
            }

            assertThat(heldCall.get(2, TimeUnit.SECONDS))
                    .isEqualTo("deepseek-completed");
        }
    }

    @Test
    void shouldReleasePermitWhenDelegateThrows() {
        ToolCallingGateway delegate = mock(ToolCallingGateway.class);
        ToolCallingRequest request = mock(ToolCallingRequest.class);
        ToolCallingModelResult result = new FinalAnswerResult(
                "request-1",
                "deepseek-v4-flash",
                "{\"answer\":\"ok\"}",
                new ModelUsage(10, 5, 15)
        );
        ModelCallBulkhead bulkhead = new ModelCallBulkhead(
                new ModelCallBulkheadProperties(1)
        );
        BulkheadToolCallingGateway gateway =
                new BulkheadToolCallingGateway(delegate, bulkhead);

        when(delegate.call(request))
                .thenThrow(new IllegalStateException("provider failed"))
                .thenReturn(result);

        assertThatThrownBy(() -> gateway.call(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider failed");

        assertThat(gateway.call(request)).isSameAs(result);
    }
}