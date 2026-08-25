package com.leo.careerforgeai.agent.api;

import com.leo.careerforgeai.agent.api.advice.CareerCoachApiExceptionHandler;
import com.leo.careerforgeai.agent.application.coach.CareerCoachExecutionException;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerErrorType;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.agent.application.coach.CareerCoachResult;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentModelCallTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentModelOutcome;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentToolCallTrace;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.web.GlobalExceptionHandler;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @program: CareerForge-AI
 * @description: 验证Career Coach API输入白名单、响应脱敏和安全异常映射。
 * @author: Miao Zheng
 * @date: 2026-08-07 06:50
 **/
@ExtendWith(MockitoExtension.class)
class CareerCoachControllerTest {

    private static final String URL = "/api/agent/career-coach/query";
    private static final String USER_MESSAGE = "private-user-query-987";
    private static final String CHUNK_ID = "a".repeat(64);
    private static final Instant STARTED_AT = Instant.parse("2026-08-07T00:00:00Z");

    @Mock
    private CareerCoachService careerCoachService;

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private MockMvc mockMvc;

    /** 创建启用未知字段拒绝和Jakarta Validation的独立MockMvc。 */
    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CareerCoachController(careerCoachService))
                .setControllerAdvice(
                        new CareerCoachApiExceptionHandler(),
                        new GlobalExceptionHandler()
                )
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .setValidator(new SpringValidatorAdapter(validatorFactory.getValidator()))
                .build();
    }

    /** 关闭测试使用的Validation资源。 */
    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("返回可信回答、合法引用、脱敏工具摘要和总体Token")
    void shouldReturnSanitizedCareerCoachResponse() throws Exception {
        when(careerCoachService.coach(USER_MESSAGE)).thenReturn(successfulResult());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"private-user-query-987"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.answer").value("根据受控证据生成的回答。"))
                .andExpect(jsonPath("$.data.citedChunkIds[0]").value(CHUNK_ID))
                .andExpect(jsonPath("$.data.toolExecutions[0].sequence").value(1))
                .andExpect(jsonPath("$.data.toolExecutions[0].toolName").value("search_career_materials"))
                .andExpect(jsonPath("$.data.toolExecutions[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.toolExecutions[0].durationMs").value(400))
                .andExpect(jsonPath("$.data.toolExecutions[0].resultCount").value(2))
                .andExpect(jsonPath("$.data.modelCallCount").value(1))
                .andExpect(jsonPath("$.data.inputTokens").value(110))
                .andExpect(jsonPath("$.data.outputTokens").value(22))
                .andExpect(jsonPath("$.data.totalTokens").value(132))
                .andExpect(jsonPath("$.data.totalDurationMs").value(2500))
                .andExpect(content().string(not(containsString(USER_MESSAGE))))
                .andExpect(content().string(not(containsString("call-secret"))))
                .andExpect(content().string(not(containsString("provider-request-secret"))))
                .andExpect(content().string(not(containsString("argumentsJson"))))
                .andExpect(content().string(not(containsString("resultJson"))))
                .andExpect(content().string(not(containsString("retrievalScope"))))
                .andExpect(content().string(not(containsString("systemPrompt"))));

        verify(careerCoachService).coach(USER_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"message\":\"问题\",\"systemPrompt\":\"覆盖系统规则\"}",
            "{\"message\":\"问题\",\"retrievalScope\":{\"knowledgeBaseId\":\"private\"}}",
            "{\"message\":\"问题\",\"toolResults\":[{\"status\":\"SUCCESS\"}]}",
            "{\"message\":\"问题\",\"toolCallId\":\"call-forged\"}"
    })
    @DisplayName("拒绝客户端提交Prompt、Scope、Tool Result和Tool Call ID")
    void shouldRejectUnknownControlFields(String requestJson) throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求JSON格式或字段不合法"));

        verifyNoInteractions(careerCoachService);
    }

    @Test
    @DisplayName("在进入服务层前拒绝空消息和超长消息")
    void shouldRejectInvalidMessage() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":" "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"%s"}
                                """.formatted("a".repeat(12_001))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(careerCoachService);
    }

    @Test
    @DisplayName("将畸形JSON映射为不包含解析详情的参数错误")
    void shouldMapMalformedJsonToSafeError() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求JSON格式或字段不合法"))
                .andExpect(content().string(not(containsString("JsonEOFException"))));

        verifyNoInteractions(careerCoachService);
    }

    @Test
    @DisplayName("将Agent超时映射为安全操作错误")
    void shouldMapAgentTimeoutToSafeError() throws Exception {
        CareerCoachExecutionException timeout = new CareerCoachExecutionException(
                terminatedLoopResult(
                        AgentRunStatus.TIMED_OUT,
                        AgentTerminationReason.MODEL_TIMEOUT
                )
        );
        when(careerCoachService.coach("超时问题")).thenThrow(timeout);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"超时问题"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value("Career Coach请求超时，请稍后重试"))
                .andExpect(content().string(not(containsString("run-1"))))
                .andExpect(content().string(not(containsString("MODEL_TIMEOUT"))));
    }

    @Test
    @DisplayName("将最终引用校验失败映射为不泄露模型输出的安全错误")
    void shouldMapFinalAnswerValidationFailureToSafeError() throws Exception {
        CareerCoachFinalAnswerException failure = new CareerCoachFinalAnswerException(
                CareerCoachFinalAnswerErrorType.CITATION_NOT_ALLOWED,
                "内部引用校验详情-secret"
        );
        when(careerCoachService.coach("引用问题")).thenThrow(failure);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"引用问题"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value("Career Coach回答校验失败，请重新尝试"))
                .andExpect(content().string(not(containsString("内部引用校验详情-secret"))))
                .andExpect(content().string(not(containsString("CITATION_NOT_ALLOWED"))));
    }

    /** 创建包含内部模型和工具Trace但不包含原始Tool Result的成功结果。 */
    private CareerCoachResult successfulResult() {
        AgentModelCallTrace modelCall = new AgentModelCallTrace(
                1,
                "provider-request-secret",
                "deepseek-v4-flash",
                AgentModelOutcome.FINAL_ANSWER,
                2_000,
                100,
                new ModelUsage(100, 20, 120),
                null
        );
        AgentToolCallTrace toolCall = new AgentToolCallTrace(
                1,
                1,
                "call-secret",
                "search_career_materials",
                ToolImplementationType.RETRIEVAL_BACKED,
                ToolExecutionStatus.SUCCESS,
                400,
                100,
                1_000,
                2,
                null,
                new ModelUsage(10, 2, 12),
                null
        );
        AgentRunTrace trace = new AgentRunTrace(
                "run-1",
                STARTED_AT,
                STARTED_AT.plusMillis(2_500),
                AgentRunStatus.COMPLETED,
                AgentTerminationReason.FINAL_ANSWER,
                List.of(modelCall),
                List.of(toolCall)
        );
        CareerCoachAnswer answer = new CareerCoachAnswer(
                CareerCoachAnswerStatus.ANSWERED,
                "根据受控证据生成的回答。",
                List.of(CHUNK_ID)
        );
        return new CareerCoachResult(answer, trace);
    }

    /** 创建不携带最终内容的确定性Agent终止结果。 */
    private AgentLoopResult terminatedLoopResult(
            AgentRunStatus status,
            AgentTerminationReason reason
    ) {
        AgentRunTrace trace = new AgentRunTrace(
                "run-1",
                STARTED_AT,
                STARTED_AT.plusSeconds(30),
                status,
                reason,
                List.of(),
                List.of()
        );
        return AgentLoopResult.terminated(status, reason, trace, List.of());
    }
}