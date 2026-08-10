package com.leo.careerforgeai.agent.infrastructure.springai.tool;

import com.leo.careerforgeai.agent.application.loop.ToolCallFingerprintService;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @program: CareerForge-AI
 * @description: 保存一次Spring AI Agent运行的可信工具上下文和执行结果。
 * @author: Miao Zheng
 * @date: 2026-08-07 19:50
 **/
public final class SpringAiToolRunContext {

    public static final String TOOL_CONTEXT_KEY = "careerforge.spring-ai.tool-run-context";
    private final Map<String, Integer> callsByToolName = new HashMap<>();
    private final Map<String, Integer> callsByFingerprint = new HashMap<>();
    private int totalToolCalls;

    private final ToolExecutionContext executionContext;
    private final AtomicInteger callSequence = new AtomicInteger();
    private final List<ToolExecutionResult> results = new ArrayList<>();
    private int modelIterations;

    public SpringAiToolRunContext(ToolExecutionContext executionContext) {
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext不能为空");
    }

    /** 从Spring AI ToolContext中取得服务端放入的可信运行上下文。 */
    public static SpringAiToolRunContext requireFrom(ToolContext toolContext) {
        if (toolContext == null) throw new IllegalArgumentException("toolContext不能为空");
        Object value = toolContext.getContext().get(TOOL_CONTEXT_KEY);
        if (!(value instanceof SpringAiToolRunContext runContext)) {
            throw new IllegalArgumentException("Spring AI工具运行上下文缺失");
        }
        return runContext;
    }

    /** 在Spring AI每次模型调用前原子校验Deadline和最大迭代次数。 */
    public synchronized int startModelIteration(int maxModelIterations, java.time.Instant now) {
        if (maxModelIterations <= 0) throw new IllegalArgumentException("maxModelIterations必须大于0");
        Objects.requireNonNull(now, "now不能为空");

        if (!now.isBefore(executionContext.deadline())) {
            throw new SpringAiToolLoopLimitException(SpringAiToolLoopLimitType.DEADLINE_EXCEEDED);
        }
        if (modelIterations >= maxModelIterations) {
            throw new SpringAiToolLoopLimitException(SpringAiToolLoopLimitType.MAX_MODEL_ITERATIONS);
        }
        return ++modelIterations;
    }

    /** 原子校验并登记一轮Spring AI模型响应中的全部Tool Calls。 */
    public synchronized void registerToolCalls(
            List<ToolCall> toolCalls,
            AgentLoopPolicy policy,
            ToolCallFingerprintService fingerprintService,
            String contextVersion
    ) {
        if (toolCalls == null || toolCalls.isEmpty() || toolCalls.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("toolCalls不能为空且不能包含null");
        }
        Objects.requireNonNull(policy, "policy不能为空");
        Objects.requireNonNull(fingerprintService, "fingerprintService不能为空");
        if (contextVersion == null || contextVersion.isBlank()) {
            throw new IllegalArgumentException("contextVersion不能为空");
        }

        if ((long) totalToolCalls + toolCalls.size() > policy.maxTotalToolCalls()) {
            throw new SpringAiToolLoopLimitException(
                    SpringAiToolLoopLimitType.MAX_TOTAL_TOOL_CALLS);
        }

        Map<String, Integer> projectedToolCounts = new HashMap<>(callsByToolName);
        Map<String, Integer> projectedFingerprintCounts = new HashMap<>(callsByFingerprint);

        for (ToolCall toolCall : toolCalls) {
            int toolCount = projectedToolCounts.merge(toolCall.name(), 1, Integer::sum);
            if (toolCount > policy.maxCallsPerTool()) {
                throw new SpringAiToolLoopLimitException(
                        SpringAiToolLoopLimitType.MAX_CALLS_PER_TOOL);
            }

            String fingerprint = fingerprintService.fingerprint(toolCall, contextVersion);
            int repeatedCount = projectedFingerprintCounts.merge(fingerprint, 1, Integer::sum);
            if (repeatedCount > policy.maxRepeatedCallCount()) {
                throw new SpringAiToolLoopLimitException(
                        SpringAiToolLoopLimitType.REPEATED_TOOL_CALL);
            }
        }

        callsByToolName.clear();
        callsByToolName.putAll(projectedToolCounts);
        callsByFingerprint.clear();
        callsByFingerprint.putAll(projectedFingerprintCounts);
        totalToolCalls += toolCalls.size();
    }

    public synchronized int totalToolCalls() {
        return totalToolCalls;
    }

    public synchronized int modelIterations() {
        return modelIterations;
    }

    /** 转换为ChatClient调用时使用的服务端Tool Context。 */
    public Map<String, Object> asToolContextMap() {
        return Map.of(TOOL_CONTEXT_KEY, this);
    }

    public ToolExecutionContext executionContext() {
        return executionContext;
    }

    /** 生成仅供项目安全执行器使用的本地关联ID，不冒充模型供应商Tool Call ID。 */
    public String nextLocalToolCallId() {
        return "spring-ai-local-" + callSequence.incrementAndGet();
    }

    /** 按实际执行顺序保存本轮工具结果，供引用校验和Trace组装使用。 */
    public synchronized void record(ToolExecutionResult result) {
        results.add(Objects.requireNonNull(result, "result不能为空"));
    }

    public synchronized List<ToolExecutionResult> results() {
        return List.copyOf(results);
    }
}