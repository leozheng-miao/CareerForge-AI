package com.leo.careerforgeai.agent.application.run.execution;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * @program: CareerForge-AI
 * @description: 显式传播白名单Run日志字段并在子任务结束后恢复原MDC
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public final class RunMdcContext {

    public static final String OWNER_ID = "ownerId";
    public static final String RUN_ID = "runId";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";

    private final Map<String, String> values;

    private RunMdcContext(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public static RunMdcContext from(RunExecutionContext context) {
        Objects.requireNonNull(context, "context不能为空");
        return new RunMdcContext(Map.of(
                OWNER_ID, context.ownerId().value(),
                RUN_ID, context.runId().toString(),
                TRACE_ID, context.traceId()
        ));
    }

    public static RunMdcContext capture() {
        Map<String, String> captured = new HashMap<>();
        captureIfPresent(captured, OWNER_ID);
        captureIfPresent(captured, RUN_ID);
        captureIfPresent(captured, TRACE_ID);
        captureIfPresent(captured, SPAN_ID);
        return new RunMdcContext(captured);
    }

    public Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task不能为空");
        return () -> run(task);
    }

    public <T> Callable<T> wrapSupplier(Supplier<T> task) {
        Objects.requireNonNull(task, "task不能为空");
        return () -> supply(task);
    }

    public void run(Runnable task) {
        Objects.requireNonNull(task, "task不能为空");
        Map<String, String> previous = install();
        try {
            task.run();
        } finally {
            restore(previous);
        }
    }

    private <T> T supply(Supplier<T> task) {
        Map<String, String> previous = install();
        try {
            return task.get();
        } finally {
            restore(previous);
        }
    }

    private Map<String, String> install() {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        String activeTraceId = previous == null ? null : previous.get(TRACE_ID);
        String activeSpanId = previous == null ? null : previous.get(SPAN_ID);

        MDC.clear();
        values.forEach(MDC::put);
        if (activeTraceId != null) MDC.put(TRACE_ID, activeTraceId);
        if (activeSpanId != null) MDC.put(SPAN_ID, activeSpanId);
        return previous;
    }

    private static void restore(Map<String, String> previous) {
        MDC.clear();
        if (previous != null) MDC.setContextMap(previous);
    }

    private static void captureIfPresent(
            Map<String, String> captured,
            String key
    ) {
        String value = MDC.get(key);
        if (value != null) captured.put(key, value);
    }
}