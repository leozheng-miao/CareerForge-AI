package com.leo.careerforgeai.agent.application.tool;

import com.leo.careerforgeai.agent.domain.tool.AgentToolOutput;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.agent.domain.tool.ToolRiskLevel;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-06 16:44
 **/
class ToolSafetyTest {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {"query": {"type": "string"}},
              "required": ["query"],
              "additionalProperties": false
            }
            """;

    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "items": {"type": "array", "items": {"type": "string"}},
                "summary": {"type": "string"}
              }
            }
            """;

    private static final Instant NOW = Instant.parse("2026-08-06T08:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = validatorFactory.getValidator();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ToolExecutionContext context = new ToolExecutionContext(
            "run-1", NOW.plusSeconds(60), new RetrievalScope("careerforge", Set.of(), Set.of()));

    @AfterEach
    void closeResources() {
        executorService.shutdownNow();
        validatorFactory.close();
    }

    @Test
    @DisplayName("Registry只允许精确名称、只读且非高风险工具")
    void shouldEnforceExplicitRegistryWhitelist() {
        TestTool allowed = tool(defaultContract("search_tool"), input -> output("ok"));

        ToolRegistry registry = new ToolRegistry(List.of(allowed));

        assertThat(registry.find("search_tool")).contains(allowed);
        assertThat(registry.find("SEARCH_TOOL")).isEmpty();
        assertThat(registry.find(" search_tool ")).isEmpty();
        assertThat(registry.definitions()).containsExactly(allowed.contract().definition());

        assertThatThrownBy(() -> new ToolRegistry(List.of(allowed, tool(defaultContract("search_tool"), input -> output("duplicate")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复工具名称");

        assertThatThrownBy(() -> new ToolRegistry(List.of(tool(
                contract("write_tool", false, ToolRiskLevel.MEDIUM, 256, 2048, 5, Duration.ofSeconds(1)),
                input -> output("write")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只读工具");

        assertThatThrownBy(() -> new ToolRegistry(List.of(tool(
                contract("dangerous_tool", true, ToolRiskLevel.HIGH, 256, 2048, 5, Duration.ofSeconds(1)),
                input -> output("danger")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("高风险工具");
    }

    @Test
    @DisplayName("合法参数执行工具并生成受控Tool Result")
    void shouldExecuteValidToolAndCreateControlledResult() throws Exception {
        SafeToolExecutor executor = executor(tool(defaultContract("search_tool"),
                input -> new TestOutput(List.of("chunk-1", "chunk-2"), input.query())));

        ToolExecutionResult result = executor.execute(
                new ToolCall("call-1", "search_tool", "{\"query\":\"Java并发\"}"), context);

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
        assertThat(result.errorType()).isNull();
        assertThat(result.resultCount()).isEqualTo(1);
        assertThat(result.modelUsage()).isNull();

        JsonNode resultJson = jsonMapper.readTree(result.resultJson());
        assertThat(resultJson.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(resultJson.get("data").get("items").size()).isEqualTo(2);
        assertThat(resultJson.get("data").get("summary").asText()).isEqualTo("Java并发");
        assertThat(resultJson.get("error").isNull()).isTrue();

        assertThat(result.toMessage().toolCallId()).isEqualTo("call-1");
        assertThat(result.toMessage().toolName()).isEqualTo("search_tool");
        assertThat(result.toMessage().content()).isEqualTo(result.resultJson());
    }

    @Test
    @DisplayName("未知工具、非法JSON、未知字段、校验失败和超长参数均不执行业务工具")
    void shouldRejectUntrustedArgumentsBeforeExecution() {
        AtomicInteger executions = new AtomicInteger();
        SafeToolExecutor executor = executor(tool(defaultContract("search_tool"), input -> {
            executions.incrementAndGet();
            return output("executed");
        }));

        assertFailure(executor.execute(
                        new ToolCall("call-1", "unknown_tool", "{\"query\":\"Java\"}"), context),
                ToolExecutionErrorType.UNKNOWN_TOOL);

        assertFailure(executor.execute(
                        new ToolCall("call-2", "search_tool", "{invalid"), context),
                ToolExecutionErrorType.INVALID_ARGUMENTS);

        assertFailure(executor.execute(
                        new ToolCall("call-3", "search_tool", "[]"), context),
                ToolExecutionErrorType.INVALID_ARGUMENTS);

        assertFailure(executor.execute(
                        new ToolCall("call-4", "search_tool", "{\"query\":\"Java\",\"class\":\"java.lang.Runtime\"}"), context),
                ToolExecutionErrorType.INVALID_ARGUMENTS);

        assertFailure(executor.execute(
                        new ToolCall("call-5", "search_tool", "{\"query\":\"\"}"), context),
                ToolExecutionErrorType.VALIDATION_FAILED);

        String oversizedArguments = "{\"query\":\"" + "x".repeat(300) + "\"}";
        assertFailure(executor.execute(
                        new ToolCall("call-6", "search_tool", oversizedArguments), context),
                ToolExecutionErrorType.INVALID_ARGUMENTS);

        assertThat(executions).hasValue(0);
    }

    @Test
    @DisplayName("总体Deadline和单工具Timeout均能确定性终止等待")
    void shouldEnforceDeadlineAndToolTimeout() {
        AtomicInteger executions = new AtomicInteger();
        TestTool normalTool = tool(defaultContract("search_tool"), input -> {
            executions.incrementAndGet();
            return output("executed");
        });

        ToolExecutionContext expiredContext = new ToolExecutionContext(
                "run-expired", NOW, new RetrievalScope("careerforge", Set.of(), Set.of()));

        assertFailure(executor(normalTool).execute(
                        new ToolCall("call-1", "search_tool", "{\"query\":\"Java\"}"), expiredContext),
                ToolExecutionErrorType.TIMEOUT);
        assertThat(executions).hasValue(0);

        TestTool blockingTool = tool(
                contract("slow_tool", true, ToolRiskLevel.LOW, 256, 2048, 5, Duration.ofMillis(20)),
                input -> {
                    while (!Thread.currentThread().isInterrupted()) LockSupport.park();
                    return output("late");
                });

        assertFailure(executor(blockingTool).execute(
                        new ToolCall("call-2", "slow_tool", "{\"query\":\"Java\"}"), context),
                ToolExecutionErrorType.TIMEOUT);
    }

    @Test
    @DisplayName("只返回显式安全业务错误并隐藏未知异常内容")
    void shouldSanitizeExecutionFailures() {
        TestTool expectedFailure = tool(defaultContract("scope_tool"), input -> {
            throw new ToolExecutionException(
                    ToolExecutionErrorType.SCOPE_VIOLATION,
                    "请求范围超出服务端允许范围",
                    new IllegalStateException("/internal/index/secret"));
        });

        ToolExecutionResult expectedResult = executor(expectedFailure).execute(
                new ToolCall("call-1", "scope_tool", "{\"query\":\"Java\"}"), context);

        assertFailure(expectedResult, ToolExecutionErrorType.SCOPE_VIOLATION);
        assertThat(expectedResult.resultJson()).contains("请求范围超出服务端允许范围");
        assertThat(expectedResult.resultJson()).doesNotContain("/internal/index/secret");

        TestTool unexpectedFailure = tool(defaultContract("broken_tool"), input -> {
            throw new IllegalStateException("api-key=secret-value");
        });

        ToolExecutionResult unexpectedResult = executor(unexpectedFailure).execute(
                new ToolCall("call-2", "broken_tool", "{\"query\":\"Java\"}"), context);

        assertFailure(unexpectedResult, ToolExecutionErrorType.EXECUTION_FAILED);
        assertThat(unexpectedResult.resultJson()).doesNotContain("api-key", "secret-value");

        TestTool oversizedSafeMessage = tool(defaultContract("oversized_error_tool"), input -> {
            throw new ToolExecutionException(ToolExecutionErrorType.SCOPE_VIOLATION, "\"".repeat(256));
        });

        ToolExecutionResult fallbackResult = executor(oversizedSafeMessage).execute(
                new ToolCall("call-3", "oversized_error_tool", "{\"query\":\"Java\"}"), context);

        assertFailure(fallbackResult, ToolExecutionErrorType.EXECUTION_FAILED);
        assertThat(fallbackResult.resultJson()).hasSizeLessThanOrEqualTo(512);
    }

    @Test
    @DisplayName("拒绝超过集合数量或序列化字符预算的工具输出")
    void shouldEnforceOutputBudgets() {
        TestTool tooManyItems = tool(
                contract("items_tool", true, ToolRiskLevel.LOW, 256, 2048, 2, Duration.ofSeconds(1)),
                input -> new TestOutput(List.of("a", "b", "c"), "summary"));

        assertFailure(executor(tooManyItems).execute(
                        new ToolCall("call-1", "items_tool", "{\"query\":\"Java\"}"), context),
                ToolExecutionErrorType.OUTPUT_LIMIT_EXCEEDED);

        TestTool tooManyCharacters = tool(
                contract("chars_tool", true, ToolRiskLevel.LOW, 256, 120, 5, Duration.ofSeconds(1)),
                input -> new TestOutput(List.of("a"), "x".repeat(300)));

        assertFailure(executor(tooManyCharacters).execute(
                        new ToolCall("call-2", "chars_tool", "{\"query\":\"Java\"}"), context),
                ToolExecutionErrorType.OUTPUT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("MODEL_BACKED工具失败时保留已观测Token和模型耗时")
    void shouldPreserveObservedModelCostOnHandledFailure() {
        ToolContract<TestInput, TestOutput> contract = modelBackedContract("parse_job_requirements");

        AgentTool<TestInput, TestOutput> modelBackedTool = new AgentTool<>() {

            /** 返回测试使用的MODEL_BACKED工具契约。 */
            @Override
            public ToolContract<TestInput, TestOutput> contract() {
                return contract;
            }

            /** 模拟模型已产生Token但结构化解析失败。 */
            @Override
            public AgentToolOutput<TestOutput> execute(TestInput input, ToolExecutionContext context) {
                return AgentToolOutput.modelBackedFailure(
                        output("结构化解析失败"),
                        ToolExecutionErrorType.EXECUTION_FAILED,
                        new ModelUsage(80, 20, 100),
                        45
                );
            }
        };

        ToolExecutionResult result = executor(modelBackedTool).execute(
                new ToolCall("call-1", "parse_job_requirements", "{\"query\":\"Java岗位\"}"),
                context
        );

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILURE);
        assertThat(result.errorType()).isEqualTo(ToolExecutionErrorType.EXECUTION_FAILED);
        assertThat(result.modelUsage()).isEqualTo(new ModelUsage(80, 20, 100));
        assertThat(result.modelDurationMs()).isEqualTo(45L);
    }

    private SafeToolExecutor executor(AgentTool<?, ?>... tools) {
        return new SafeToolExecutor(
                new ToolRegistry(List.of(tools)), jsonMapper, validator, executorService, clock);
    }

    /** 创建测试使用的MODEL_BACKED工具契约。 */
    private ToolContract<TestInput, TestOutput> modelBackedContract(String name) {
        return new ToolContract<>(
                new ToolDefinition(name, "测试模型工具", INPUT_SCHEMA),
                OUTPUT_SCHEMA,
                TestInput.class,
                TestOutput.class,
                ToolImplementationType.MODEL_BACKED,
                ToolRiskLevel.LOW,
                true,
                256,
                2048,
                5,
                Duration.ofSeconds(1)
        );
    }

    private TestTool tool(
            ToolContract<TestInput, TestOutput> contract,
            Function<TestInput, TestOutput> action
    ) {
        return new TestTool(contract, action);
    }

    private ToolContract<TestInput, TestOutput> defaultContract(String name) {
        return contract(name, true, ToolRiskLevel.LOW, 256, 2048, 5, Duration.ofSeconds(1));
    }

    private ToolContract<TestInput, TestOutput> contract(
            String name,
            boolean readOnly,
            ToolRiskLevel riskLevel,
            int maxArgumentsChars,
            int maxResultChars,
            int maxResultItems,
            Duration timeout
    ) {
        return new ToolContract<>(
                new ToolDefinition(name, "测试工具", INPUT_SCHEMA),
                OUTPUT_SCHEMA,
                TestInput.class,
                TestOutput.class,
                ToolImplementationType.DETERMINISTIC,
                riskLevel,
                readOnly,
                maxArgumentsChars,
                maxResultChars,
                maxResultItems,
                timeout
        );
    }

    private TestOutput output(String summary) {
        return new TestOutput(List.of("chunk-1"), summary);
    }

    private void assertFailure(
            ToolExecutionResult result,
            ToolExecutionErrorType expectedError
    ) {
        assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILURE);
        assertThat(result.errorType()).isEqualTo(expectedError);
        assertThat(result.resultJson()).doesNotContain(
                "java.lang",
                "/Users/",
                "secret-value"
        );
    }

    private record TestInput(
            @NotBlank
            @Size(max = 20)
            String query
    ) {
    }

    private record TestOutput(
            List<String> items,
            String summary
    ) {
    }

    private record TestTool(
            ToolContract<TestInput, TestOutput> contract,
            Function<TestInput, TestOutput> action
    ) implements AgentTool<TestInput, TestOutput> {

        @Override
        public AgentToolOutput<TestOutput> execute(
                TestInput input,
                ToolExecutionContext context
        ) {
            return AgentToolOutput.of(action.apply(input), 1);
        }
    }
}