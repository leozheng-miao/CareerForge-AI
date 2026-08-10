package com.leo.careerforgeai.agent.infrastructure.springai.coach;

import com.leo.careerforgeai.agent.application.coach.CareerCoachDefinition;
import com.leo.careerforgeai.agent.application.coach.CareerCoachFinalAnswerValidator;
import com.leo.careerforgeai.agent.application.coach.CareerCoachScopeProvider;
import com.leo.careerforgeai.agent.application.tool.career.parse.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.lifecycle.SpringAiToolLoopLimitException;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.lifecycle.SpringAiToolLoopLimitType;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.lifecycle.SpringAiToolRunContext;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.provider.Arguments;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 使用内存ChatModel验证Spring AI Career Coach的请求组装和共享结果校验。
 * @author: Miao Zheng
 * @date: 2026-08-10 02:40
 **/
class SpringAiCareerCoachServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("使用共享Prompt工具Scope和模型参数生成可信回答")
    void shouldBuildControlledSpringAiRequestAndReturnTrustedAnswer() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {"status":"ANSWERED","answer":"这是Spring AI对照回答。","citedChunkIds":[]}
                """);
        ToolCallback toolCallback = new NoOpToolCallback();
        SpringAiCareerCoachService service = new SpringAiCareerCoachService(
                ChatClient.create(chatModel),
                new CareerCoachFinalAnswerValidator(
                        JsonMapper.builder().build(),
                        validatorFactory.getValidator()
                ),
                scopeProvider(),
                List.of(toolCallback),
                policy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        SpringAiCareerCoachResult result = service.coach("  请给我一般职业建议。  ");

        assertThat(result.answer().status()).isEqualTo(CareerCoachAnswerStatus.ANSWERED);
        assertThat(result.answer().answer()).isEqualTo("这是Spring AI对照回答。");
        assertThat(result.answer().citedChunkIds()).isEmpty();
        assertThat(result.toolResults()).isEmpty();
        assertThat(result.totalDurationMs()).isZero();

        Prompt prompt = chatModel.capturedPrompt();
        assertThat(prompt.getSystemMessage().getText()).isEqualTo(CareerCoachDefinition.SYSTEM_PROMPT);
        assertThat(prompt.getUserMessage().getText()).isEqualTo("请给我一般职业建议。");
        assertThat(prompt.getOptions()).isInstanceOf(DeepSeekChatOptions.class);

        DeepSeekChatOptions options = (DeepSeekChatOptions) prompt.getOptions();
        assertThat(options.getMaxTokens()).isEqualTo(2_000);
        assertThat(options.getToolChoice()).isEqualTo("auto");
        assertThat(options.getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
        assertThat(options.getToolCallbacks()).containsExactly(toolCallback);

        Object contextValue = options.getToolContext().get(SpringAiToolRunContext.TOOL_CONTEXT_KEY);
        assertThat(contextValue).isInstanceOf(SpringAiToolRunContext.class);
        SpringAiToolRunContext runContext = (SpringAiToolRunContext) contextValue;
        assertThat(runContext.executionContext().agentRunId()).isEqualTo(result.runId());
        assertThat(runContext.executionContext().retrievalScope().knowledgeBaseId())
                .isEqualTo("careerforge-career-materials");
        assertThat(runContext.executionContext().retrievalScope().documentIds())
                .containsExactly("job-document");
    }

    @Test
    @DisplayName("由ChatClient默认生命周期执行工具并继续生成带合法引用的最终回答")
    void shouldExecuteToolAndContinueToFinalAnswer() {
        String chunkId = "a".repeat(64);
        String toolResultJson = """
            {
              "status": "SUCCESS",
              "data": {
                "status": "SUCCESS",
                "requestId": "request-1",
                "evidence": [{
                  "chunkId": "%s",
                  "documentId": "job-document",
                  "documentName": "岗位JD.md",
                  "documentType": "JOB_DESCRIPTION",
                  "sectionPath": ["技能要求"],
                  "content": "Spring AI"
                }],
                "usedContentChars": 9,
                "candidateCount": 1,
                "errorType": null
              },
              "error": null
            }
            """.formatted(chunkId);
        String finalContent = """
            {"status":"ANSWERED","answer":"岗位要求掌握Spring AI。","citedChunkIds":["%s"]}
            """.formatted(chunkId);

        ScriptedToolCallingChatModel chatModel = new ScriptedToolCallingChatModel(finalContent);
        RecordingEvidenceToolCallback toolCallback = new RecordingEvidenceToolCallback(toolResultJson);
        SpringAiCareerCoachService service = new SpringAiCareerCoachService(
                ChatClient.create(chatModel),
                new CareerCoachFinalAnswerValidator(
                        JsonMapper.builder().build(),
                        validatorFactory.getValidator()
                ),
                scopeProvider(),
                List.of(toolCallback),
                policy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        SpringAiCareerCoachResult result = service.coach("请根据岗位材料分析Spring AI要求。");

        assertThat(chatModel.prompts()).hasSize(2);
        assertThat(toolCallback.callCount()).isEqualTo(1);
        assertThat(toolCallback.arguments())
                .isEqualTo("{\"query\":\"Spring AI岗位要求\"}");

        assertThat(result.answer().status()).isEqualTo(CareerCoachAnswerStatus.ANSWERED);
        assertThat(result.answer().citedChunkIds()).containsExactly(chunkId);
        assertThat(result.toolResults()).hasSize(1);

        ToolExecutionResult recordedResult = result.toolResults().getFirst();
        assertThat(recordedResult.toolName()).isEqualTo(SearchCareerMaterialsTool.NAME);
        assertThat(recordedResult.toolCallId()).isEqualTo("spring-ai-local-1");

        ToolResponseMessage toolResponseMessage = chatModel.prompts().get(1).getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(toolResponseMessage.getResponses()).hasSize(1);
        ToolResponseMessage.ToolResponse response = toolResponseMessage.getResponses().getFirst();
        assertThat(response.id()).isEqualTo("provider-call-1");
        assertThat(response.name()).isEqualTo(SearchCareerMaterialsTool.NAME);
        assertThat(response.responseData()).isEqualTo(toolResultJson);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("springAiFailures")
    @DisplayName("将Spring AI内部异常映射为稳定安全的执行错误")
    void shouldMapSpringAiFailures(
            String caseName,
            RuntimeException cause,
            SpringAiCareerCoachErrorType expectedErrorType
    ) {
        ChatModel failingChatModel = prompt -> {
            throw cause;
        };
        SpringAiCareerCoachService service = new SpringAiCareerCoachService(
                ChatClient.create(failingChatModel),
                new CareerCoachFinalAnswerValidator(
                        JsonMapper.builder().build(),
                        validatorFactory.getValidator()
                ),
                scopeProvider(),
                List.of(new NoOpToolCallback()),
                policy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.coach("测试Spring AI异常映射"))
                .isInstanceOfSatisfying(
                        SpringAiCareerCoachExecutionException.class,
                        exception -> {
                            assertThat(exception.getErrorType()).isEqualTo(expectedErrorType);
                            assertThat(exception.getRunId()).isNotBlank();
                            assertThat(exception.getToolResults()).isEmpty();
                            assertThat(exception.getCause()).isSameAs(cause);
                            assertThat(exception.getMessage())
                                    .isEqualTo("Spring AI Career Coach未能完成本次请求")
                                    .doesNotContain("provider-secret");
                        }
                );
    }

    @Test
    @DisplayName("记录默认ToolCallingAdvisor不会执行AgentLoopPolicy迭代限制")
    void shouldExposeDefaultAdvisorIterationLimitGap() {
        AgentLoopPolicy restrictivePolicy = new AgentLoopPolicy(
                2, 8, 4, 2, 20_000, 2_000, 80_000,
                Duration.ofSeconds(60), Duration.ofSeconds(30)
        );
        OverIterationChatModel chatModel = new OverIterationChatModel();
        SpringAiCareerCoachService service = new SpringAiCareerCoachService(
                ChatClient.create(chatModel),
                new CareerCoachFinalAnswerValidator(
                        JsonMapper.builder().build(),
                        validatorFactory.getValidator()
                ),
                scopeProvider(),
                List.of(new NoOpToolCallback()),
                restrictivePolicy,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        SpringAiCareerCoachResult result = service.coach("测试默认工具循环的迭代边界。");

        assertThat(result.answer().status()).isEqualTo(CareerCoachAnswerStatus.ANSWERED);
        assertThat(restrictivePolicy.maxModelIterations()).isEqualTo(2);
        assertThat(chatModel.callCount()).isEqualTo(3)
                .isGreaterThan(restrictivePolicy.maxModelIterations());
    }

    private CareerCoachScopeProvider scopeProvider() {
        KnowledgeSourceProperties properties = new KnowledgeSourceProperties(
                "careerforge-career-materials",
                Path.of("."),
                List.of(new KnowledgeSourceProperties.DocumentDefinition(
                        "job-document",
                        "岗位JD.md",
                        KnowledgeDocumentType.JOB_DESCRIPTION,
                        "岗位JD.md"
                ))
        );
        return new CareerCoachScopeProvider(properties);
    }

    private AgentLoopPolicy policy() {
        return new AgentLoopPolicy(
                6, 8, 4, 2, 20_000, 2_000, 80_000,
                Duration.ofSeconds(60), Duration.ofSeconds(30)
        );
    }

    private static Stream<Arguments> springAiFailures() {
        ToolDefinition toolDefinition = new DefaultToolDefinition(
                "test_tool",
                "测试工具",
                "{\"type\":\"object\",\"properties\":{}}"
        );

        return Stream.of(
                Arguments.of(
                        "临时模型故障",
                        new TransientAiException("provider-secret"),
                        SpringAiCareerCoachErrorType.TRANSIENT_MODEL_FAILURE
                ),
                Arguments.of(
                        "非临时模型故障",
                        new NonTransientAiException("provider-secret"),
                        SpringAiCareerCoachErrorType.NON_TRANSIENT_MODEL_FAILURE
                ),
                Arguments.of(
                        "工具框架故障",
                        new ToolExecutionException(
                                toolDefinition,
                                new IllegalStateException("provider-secret")
                        ),
                        SpringAiCareerCoachErrorType.TOOL_EXECUTION_FAILURE
                ),
                Arguments.of(
                        "Agent循环达到限制",
                        new SpringAiToolLoopLimitException(
                                SpringAiToolLoopLimitType.MAX_MODEL_ITERATIONS
                        ),
                        SpringAiCareerCoachErrorType.LIMIT_EXCEEDED
                ),
                Arguments.of(
                        "未知框架故障",
                        new IllegalStateException("provider-secret"),
                        SpringAiCareerCoachErrorType.FRAMEWORK_FAILURE
                )
        );
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存ChatClient实际提交的Prompt并返回固定最终回答。
     * @author: Miao Zheng
     * @date: 2026-08-10 02:40
     **/
    private static final class RecordingChatModel implements ChatModel {

        private final String responseContent;
        private Prompt capturedPrompt;

        private RecordingChatModel(String responseContent) {
            this.responseContent = responseContent;
        }

        @Override
        public DeepSeekChatOptions getOptions() {
            return DeepSeekChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.capturedPrompt = prompt;
            return new ChatResponse(List.of(
                    new Generation(new AssistantMessage(responseContent))
            ));
        }

        private Prompt capturedPrompt() {
            return capturedPrompt;
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 为请求组装测试提供不会被实际调用的内存ToolCallback。
     * @author: Miao Zheng
     * @date: 2026-08-10 02:40
     **/
    private static final class NoOpToolCallback implements ToolCallback {

        private static final ToolDefinition DEFINITION = new DefaultToolDefinition(
                "test_tool",
                "仅用于测试请求组装",
                "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"
        );

        @Override
        public ToolDefinition getToolDefinition() {
            return DEFINITION;
        }

        @Override
        public String call(String arguments) {
            return "{\"status\":\"SUCCESS\"}";
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 依次返回工具调用和最终回答，用于验证ChatClient默认Tool Calling循环。
     * @author: Miao Zheng
     * @date: 2026-08-10 03:00
     **/
    private static final class ScriptedToolCallingChatModel implements ChatModel {

        private final String finalContent;
        private final List<Prompt> prompts = new ArrayList<>();

        private ScriptedToolCallingChatModel(String finalContent) {
            this.finalContent = finalContent;
        }

        @Override
        public DeepSeekChatOptions getOptions() {
            return DeepSeekChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            if (prompts.size() == 1) return toolCallResponse();
            if (prompts.size() == 2) return finalAnswerResponse();
            throw new AssertionError("ChatClient发生了非预期的额外模型调用");
        }

        private ChatResponse toolCallResponse() {
            AssistantMessage message = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            "provider-call-1",
                            "function",
                            SearchCareerMaterialsTool.NAME,
                            "{\"query\":\"Spring AI岗位要求\"}"
                    )))
                    .build();
            return new ChatResponse(List.of(new Generation(
                    message,
                    ChatGenerationMetadata.builder().finishReason("tool_calls").build()
            )));
        }

        private ChatResponse finalAnswerResponse() {
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage(finalContent),
                    ChatGenerationMetadata.builder().finishReason("stop").build()
            )));
        }

        private List<Prompt> prompts() {
            return List.copyOf(prompts);
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 模拟证据ToolCallback并将执行结果记录到服务端Spring AI运行上下文。
     * @author: Miao Zheng
     * @date: 2026-08-10 03:00
     **/
    private static final class RecordingEvidenceToolCallback implements ToolCallback {

        private final String resultJson;
        private int callCount;
        private String arguments;

        private RecordingEvidenceToolCallback(String resultJson) {
            this.resultJson = resultJson;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return new DefaultToolDefinition(
                    SearchCareerMaterialsTool.NAME,
                    "测试职业材料搜索工具",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "query": {"type": "string"}
                      },
                      "required": ["query"],
                      "additionalProperties": false
                    }
                    """
            );
        }

        @Override
        public String call(String arguments) {
            throw new AssertionError("必须使用携带服务端ToolContext的调用入口");
        }

        @Override
        public String call(String arguments, ToolContext toolContext) {
            callCount++;
            this.arguments = arguments;

            SpringAiToolRunContext runContext = SpringAiToolRunContext.requireFrom(toolContext);
            ToolExecutionResult result = ToolExecutionResult.success(
                    runContext.nextLocalToolCallId(),
                    SearchCareerMaterialsTool.NAME,
                    resultJson,
                    1,
                    null,
                    null
            );
            runContext.record(result);
            return resultJson;
        }

        private int callCount() {
            return callCount;
        }

        private String arguments() {
            return arguments;
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 连续返回两轮工具调用后才返回最终回答，用于复现默认Advisor忽略项目迭代上限。
     * @author: Miao Zheng
     * @date: 2026-08-10 04:40
     **/
    private static final class OverIterationChatModel implements ChatModel {

        private int callCount;

        @Override
        public DeepSeekChatOptions getOptions() {
            return DeepSeekChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            callCount++;
            if (callCount <= 2) {
                AssistantMessage message = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "provider-call-" + callCount,
                                "function",
                                "test_tool",
                                "{}"
                        )))
                        .build();
                return new ChatResponse(List.of(new Generation(
                        message,
                        ChatGenerationMetadata.builder()
                                .finishReason("tool_calls")
                                .build()
                )));
            }

            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("""
                        {"status":"ANSWERED","answer":"默认Advisor继续执行了第三轮模型调用。","citedChunkIds":[]}
                        """),
                    ChatGenerationMetadata.builder()
                            .finishReason("stop")
                            .build()
            )));
        }

        private int callCount() {
            return callCount;
        }
    }
}