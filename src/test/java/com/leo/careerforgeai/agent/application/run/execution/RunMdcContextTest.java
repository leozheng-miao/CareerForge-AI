package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证Run MDC对白名单字段的显式传播、非白名单隔离和线程清理
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class RunMdcContextTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID RUN_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-20T10:00:00Z");

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();

    @AfterEach
    void closeResources() {
        MDC.clear();
        executorService.shutdownNow();
    }

    @Test
    void shouldPropagateOnlyWhitelistedFieldsAndCleanWorkerThread()
            throws Exception {
        MDC.put(RunMdcContext.OWNER_ID, OWNER.value());
        MDC.put(RunMdcContext.RUN_ID, RUN_ID.toString());
        MDC.put(RunMdcContext.TRACE_ID, "trace-run-1");
        MDC.put("unsafeField", "不能传播");

        RunMdcContext captured = RunMdcContext.capture();
        MDC.clear();

        Future<MdcValues> propagated = executorService.submit(
                captured.wrapSupplier(
                        () -> new MdcValues(
                                MDC.get(RunMdcContext.OWNER_ID),
                                MDC.get(RunMdcContext.RUN_ID),
                                MDC.get(RunMdcContext.TRACE_ID),
                                MDC.get("unsafeField")
                        )
                )
        );

        MdcValues values = propagated.get(2, TimeUnit.SECONDS);

        assertThat(values.ownerId()).isEqualTo(OWNER.value());
        assertThat(values.runId()).isEqualTo(RUN_ID.toString());
        assertThat(values.traceId()).isEqualTo("trace-run-1");
        assertThat(values.unsafeField()).isNull();

        Future<String> afterTask = executorService.submit(
                () -> MDC.get(RunMdcContext.RUN_ID)
        );

        assertThat(afterTask.get(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void shouldBuildContextDirectlyFromRunExecutionContext() throws Exception {
        RunExecutionContext executionContext = new RunExecutionContext(
                OWNER,
                RUN_ID,
                "trace-run-2",
                NOW,
                NOW.plusSeconds(60)
        );

        Future<String> propagated = executorService.submit(
                RunMdcContext.from(executionContext).wrapSupplier(
                        () -> MDC.get(RunMdcContext.TRACE_ID)
                )
        );

        assertThat(propagated.get(2, TimeUnit.SECONDS))
                .isEqualTo("trace-run-2");
    }

    @Test
    void shouldPreserveActiveTracingFieldsAndRemoveUntrustedMdcInsideRun() {
        MDC.put(RunMdcContext.TRACE_ID, "0123456789abcdef0123456789abcdef");
        MDC.put(RunMdcContext.SPAN_ID, "0123456789abcdef");
        MDC.put("unsafeField", "untrusted");

        RunMdcContext.from(new RunExecutionContext(
                OWNER, RUN_ID, "fallback-trace", NOW, NOW.plusSeconds(60)
        )).run(() -> {
            assertThat(MDC.get(RunMdcContext.TRACE_ID))
                    .isEqualTo("0123456789abcdef0123456789abcdef");
            assertThat(MDC.get(RunMdcContext.SPAN_ID)).isEqualTo("0123456789abcdef");
            assertThat(MDC.get("unsafeField")).isNull();
            assertThat(MDC.get(RunMdcContext.RUN_ID)).isEqualTo(RUN_ID.toString());
        });

        assertThat(MDC.get(RunMdcContext.TRACE_ID))
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(MDC.get("unsafeField")).isEqualTo("untrusted");
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存MDC传播测试读取到的日志字段
     * @author: Miao Zheng
     * @date: 2026-08-20
     * @param ownerId owner日志字段
     * @param runId Run日志字段
     * @param traceId Trace日志字段
     * @param unsafeField 不应传播的非白名单字段
     **/
    private record MdcValues(
            String ownerId,
            String runId,
            String traceId,
            String unsafeField
    ) {
    }
}