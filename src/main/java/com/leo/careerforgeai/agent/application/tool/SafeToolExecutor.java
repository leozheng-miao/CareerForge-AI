package com.leo.careerforgeai.agent.application.tool;

import com.leo.careerforgeai.agent.domain.tool.AgentToolOutput;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @program: CareerForge-AI
 * @description: 安全完成工具白名单查找、参数校验、超时执行、结果序列化和内部模型观测数据传递。
 * @author: Miao Zheng
 * @date: 2026-08-07 00:10
 **/
@Slf4j
public final class SafeToolExecutor {

    private static final String FALLBACK_FAILURE_JSON =
            "{\"status\":\"FAILURE\",\"data\":null,\"error\":{\"type\":\"EXECUTION_FAILED\",\"message\":\"工具执行失败\"}}";
    private static final int MAX_FAILURE_RESULT_CHARS = 512;

    private final ToolRegistry registry;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final ExecutorService executorService;
    private final Clock clock;

    public SafeToolExecutor(ToolRegistry registry, JsonMapper jsonMapper, Validator validator,
                            ExecutorService executorService, Clock clock) {
        this.registry = registry;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.executorService = executorService;
        this.clock = clock;
    }

    /** 安全执行一次模型请求的工具调用。 */
    public ToolExecutionResult execute(ToolCall toolCall, ToolExecutionContext context) {
        if (toolCall == null) throw new IllegalArgumentException("toolCall 不能为空");
        if (context == null) throw new IllegalArgumentException("context 不能为空");

        Optional<AgentTool<?, ?>> registered = registry.find(toolCall.name());
        if (registered.isEmpty()) {
            return failure(toolCall, ToolExecutionErrorType.UNKNOWN_TOOL, "工具不可用");
        }

        try {
            return executeRegistered(toolCall, context, registered.get());
        } catch (RuntimeException exception) {
            log.warn("工具执行器内部失败，agentRunId={}, toolCallId={}, toolName={}, exceptionType={}",
                    safeLogValue(context.agentRunId()), safeLogValue(toolCall.id()),
                    toolCall.name(), exception.getClass().getSimpleName());
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "工具执行失败");
        }
    }

    /** 将通配符工具转交给类型安全的执行方法。 */
    private ToolExecutionResult executeRegistered(ToolCall toolCall, ToolExecutionContext context,
                                                  AgentTool<?, ?> registeredTool) {
        return executeTyped(toolCall, context, registeredTool);
    }

    /** 校验工具参数和剩余时间后执行具体工具。 */
    private <I, O> ToolExecutionResult executeTyped(ToolCall toolCall, ToolExecutionContext context,
                                                    AgentTool<I, O> tool) {
        ToolContract<I, O> contract = tool.contract();

        if (toolCall.argumentsJson().length() > contract.maxArgumentsChars()) {
            return failure(toolCall, ToolExecutionErrorType.INVALID_ARGUMENTS, "工具参数超过长度限制");
        }

        I input = parseInput(toolCall, contract);
        if (input == null) {
            return failure(toolCall, ToolExecutionErrorType.INVALID_ARGUMENTS, "工具参数不是合法 JSON 对象");
        }

        Set<ConstraintViolation<I>> violations = validator.validate(input);
        if (!violations.isEmpty()) {
            return failure(toolCall, ToolExecutionErrorType.VALIDATION_FAILED, "工具参数校验失败");
        }

        Duration remaining = Duration.between(clock.instant(), context.deadline());
        if (remaining.isZero() || remaining.isNegative()) {
            return failure(toolCall, ToolExecutionErrorType.TIMEOUT, "Agent Deadline 已到期");
        }

        Duration executionTimeout = min(contract.timeout(), remaining);
        return invoke(toolCall, context, tool, input, contract, executionTimeout);
    }

    /** 将不可信arguments严格解析为工具输入对象。 */
    private <I> I parseInput(ToolCall toolCall, ToolContract<I, ?> contract) {
        try {
            JsonNode inputNode = jsonMapper.readTree(toolCall.argumentsJson());
            if (inputNode == null || !inputNode.isObject()) return null;

            return jsonMapper.readerFor(contract.inputType())
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(toolCall.argumentsJson());
        } catch (JacksonException exception) {
            return null;
        }
    }

    /** 在单工具Timeout和Agent Deadline共同限制内执行工具。 */
    private <I, O> ToolExecutionResult invoke(ToolCall toolCall, ToolExecutionContext context,
                                              AgentTool<I, O> tool, I input,
                                              ToolContract<I, O> contract,
                                              Duration executionTimeout) {
        Future<AgentToolOutput<O>> future;

        try {
            future = executorService.submit(() -> tool.execute(input, context));
        } catch (RuntimeException exception) {
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "工具执行任务无法提交");
        }

        try {
            AgentToolOutput<O> output = future.get(toTimeoutNanos(executionTimeout), TimeUnit.NANOSECONDS);

            if (!clock.instant().isBefore(context.deadline())) {
                return failure(toolCall, ToolExecutionErrorType.TIMEOUT, "Agent Deadline 已到期");
            }

            return mapOutput(toolCall, output, contract);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return failure(toolCall, ToolExecutionErrorType.TIMEOUT, "工具执行超时");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "工具执行被中断");
        } catch (ExecutionException exception) {
            return mapExecutionFailure(toolCall, context, exception.getCause());
        }
    }

    /** 将工具抛出的异常转换为不泄露内部信息的失败结果。 */
    private ToolExecutionResult mapExecutionFailure(ToolCall toolCall, ToolExecutionContext context,
                                                    Throwable cause) {
        if (cause instanceof ToolExecutionException exception) {
            return failure(toolCall, exception.getErrorType(), exception.getMessage());
        }

        String exceptionType = cause == null ? "Unknown" : cause.getClass().getSimpleName();
        log.warn("业务工具执行失败，agentRunId={}, toolCallId={}, toolName={}, exceptionType={}",
                safeLogValue(context.agentRunId()), safeLogValue(toolCall.id()),
                toolCall.name(), exceptionType);

        return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "工具执行失败");
    }

    /** 校验并序列化成功或已处理失败的工具业务输出。 */
    private <O> ToolExecutionResult mapOutput(ToolCall toolCall, AgentToolOutput<O> output,
                                              ToolContract<?, O> contract) {
        if (output == null || !contract.outputType().isInstance(output.data())) {
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "工具返回结果类型错误");
        }

        boolean modelBacked = contract.implementationType() == ToolImplementationType.MODEL_BACKED;

        if (output.status() == ToolExecutionStatus.SUCCESS
                && modelBacked
                && (output.modelUsage() == null || output.modelDurationMs() == null)) {
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "模型驱动工具缺少内部模型观测数据");
        }

        if (!modelBacked && output.modelDurationMs() != null) {
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "当前工具类型不能声明内部模型耗时");
        }

        if (output.status() == ToolExecutionStatus.FAILURE
                && !modelBacked
                && output.modelUsage() != null) {
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "当前工具类型不能声明失败模型Token");
        }

        if (output.status() == ToolExecutionStatus.FAILURE
                && modelBacked
                && output.modelUsage() != null
                && output.modelDurationMs() == null) {
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "模型驱动工具失败结果缺少内部模型耗时");
        }

        if (output.status() == ToolExecutionStatus.SUCCESS
                && contract.implementationType() != ToolImplementationType.MODEL_BACKED
                && contract.implementationType() != ToolImplementationType.RETRIEVAL_BACKED
                && output.modelUsage() != null) {
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED, "当前工具类型不能声明内部模型Token");
        }

        if (output.status() == ToolExecutionStatus.SUCCESS
                && output.resultCount() != null
                && output.resultCount() > contract.maxResultItems()) {
            return failure(toolCall, ToolExecutionErrorType.OUTPUT_LIMIT_EXCEEDED,
                    "工具返回数量超过限制", output.modelUsage(), output.modelDurationMs());
        }

        try {
            String outputJson = jsonMapper.writeValueAsString(output.data());
            JsonNode outputNode = jsonMapper.readTree(outputJson);

            if (outputNode == null) {
                return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED,
                        "工具返回结果无法序列化", output.modelUsage(), output.modelDurationMs());
            }

            if (exceedsCollectionLimit(outputNode, contract.maxResultItems())) {
                return failure(toolCall, ToolExecutionErrorType.OUTPUT_LIMIT_EXCEEDED,
                        "工具返回集合超过数量限制", output.modelUsage(), output.modelDurationMs());
            }

            ErrorEnvelope error = output.status() == ToolExecutionStatus.FAILURE
                    ? new ErrorEnvelope(output.errorType(), safeFailureMessage(output.errorType()))
                    : null;
            String resultJson = jsonMapper.writeValueAsString(
                    new ResultEnvelope(output.status(), outputNode, error));

            int resultLimit = output.status() == ToolExecutionStatus.FAILURE
                    ? Math.min(contract.maxResultChars(), MAX_FAILURE_RESULT_CHARS)
                    : contract.maxResultChars();

            if (resultJson.length() > resultLimit) {
                return output.status() == ToolExecutionStatus.FAILURE
                        ? fallbackFailure(toolCall, output.modelUsage(), output.modelDurationMs())
                        : failure(toolCall, ToolExecutionErrorType.OUTPUT_LIMIT_EXCEEDED,
                        "工具返回内容超过长度限制", output.modelUsage(), output.modelDurationMs());
            }

            if (output.status() == ToolExecutionStatus.FAILURE) {
                return ToolExecutionResult.failure(
                        toolCall.id(),
                        toolCall.name(),
                        resultJson,
                        output.errorType(),
                        output.modelUsage(),
                        output.modelDurationMs()
                );
            }

            return ToolExecutionResult.success(
                    toolCall.id(),
                    toolCall.name(),
                    resultJson,
                    output.resultCount(),
                    output.modelUsage(),
                    output.modelDurationMs()
            );
        } catch (JacksonException exception) {
            return failure(toolCall, ToolExecutionErrorType.EXECUTION_FAILED,
                    "工具返回结果无法序列化", output.modelUsage(), output.modelDurationMs());
        }
    }

    /** 根据安全错误类型生成不包含内部详情的固定消息。 */
    private String safeFailureMessage(ToolExecutionErrorType errorType) {
        return switch (errorType) {
            case TIMEOUT -> "工具执行超时";
            case SCOPE_VIOLATION -> "工具访问范围不合法";
            case INVALID_ARGUMENTS, VALIDATION_FAILED -> "工具参数不合法";
            case OUTPUT_LIMIT_EXCEEDED -> "工具输出超过限制";
            case UNKNOWN_TOOL -> "工具不可用";
            case EXECUTION_FAILED -> "工具执行失败";
        };
    }

    /** 递归检查输出中的所有集合是否超过Contract限制。 */
    private boolean exceedsCollectionLimit(JsonNode node, int maxItems) {
        if (node.isArray() && node.size() > maxItems) return true;

        for (JsonNode child : node) {
            if (exceedsCollectionLimit(child, maxItems)) return true;
        }

        return false;
    }

    /** 创建不包含内部模型观测数据的安全失败结果。 */
    private ToolExecutionResult failure(ToolCall toolCall, ToolExecutionErrorType errorType,
                                        String safeMessage) {
        return failure(toolCall, errorType, safeMessage, null, null);
    }

    /** 创建保留已观测内部模型成本的安全失败结果。 */
    private ToolExecutionResult failure(ToolCall toolCall, ToolExecutionErrorType errorType,
                                        String safeMessage, ModelUsage modelUsage,
                                        Long modelDurationMs) {
        try {
            String resultJson = jsonMapper.writeValueAsString(new ResultEnvelope(
                    ToolExecutionStatus.FAILURE,
                    null,
                    new ErrorEnvelope(errorType, safeMessage)
            ));

            if (resultJson.length() > MAX_FAILURE_RESULT_CHARS) {
                return fallbackFailure(toolCall, modelUsage, modelDurationMs);
            }

            return ToolExecutionResult.failure(
                    toolCall.id(),
                    toolCall.name(),
                    resultJson,
                    errorType,
                    modelUsage,
                    modelDurationMs
            );
        } catch (JacksonException exception) {
            return fallbackFailure(toolCall, modelUsage, modelDurationMs);
        }
    }

    /** 创建不包含内部模型观测数据的固定兜底失败结果。 */
    private ToolExecutionResult fallbackFailure(ToolCall toolCall) {
        return fallbackFailure(toolCall, null, null);
    }

    /** 创建保留已观测模型成本的固定兜底失败结果。 */
    private ToolExecutionResult fallbackFailure(ToolCall toolCall, ModelUsage modelUsage,
                                                Long modelDurationMs) {
        return ToolExecutionResult.failure(
                toolCall.id(),
                toolCall.name(),
                FALLBACK_FAILURE_JSON,
                ToolExecutionErrorType.EXECUTION_FAILED,
                modelUsage,
                modelDurationMs
        );
    }

    /** 返回两个Duration中较小的一个。 */
    private Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    /** 将执行超时安全转换为Future等待使用的纳秒数。 */
    private long toTimeoutNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    /** 清理日志关联值中的控制字符并限制长度。 */
    private String safeLogValue(String value) {
        String sanitized = value.replace('\r', '_').replace('\n', '_').replace('\t', '_');
        return sanitized.length() <= 128 ? sanitized : sanitized.substring(0, 128);
    }

    private record ResultEnvelope(ToolExecutionStatus status, JsonNode data, ErrorEnvelope error) {
    }

    private record ErrorEnvelope(ToolExecutionErrorType type, String message) {
    }
}