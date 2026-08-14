package com.leo.careerforgeai.agent.api;

import com.leo.careerforgeai.agent.api.advice.CareerCoachApiExceptionHandler;
import com.leo.careerforgeai.agent.application.coach.CareerCoachResult;
import com.leo.careerforgeai.agent.application.coach.ConversationalCareerCoachApplicationService;
import com.leo.careerforgeai.agent.application.coach.ConversationalCareerCoachResult;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.web.GlobalExceptionHandler;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @program: CareerForge-AI
 * @description: 验证会话API的输入白名单、版本前置条件、响应脱敏和安全异常映射
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
@ExtendWith(MockitoExtension.class)
class CoachingSessionControllerTest {

    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final ActorId ACTOR_ID = new ActorId("actor-a");
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final String BASE_URL = "/api/coaching-sessions";
    private static final String USER_MESSAGE = "请继续解释乐观锁";

    @Mock
    private CoachingSessionApplicationService sessionApplicationService;

    @Mock
    private ConversationalCareerCoachApplicationService conversationalCareerCoachService;

    private final ValidatorFactory validatorFactory =
            Validation.buildDefaultValidatorFactory();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CoachingSessionController(
                                sessionApplicationService,
                                conversationalCareerCoachService
                        )
                )
                .setControllerAdvice(
                        new CareerCoachApiExceptionHandler(),
                        new GlobalExceptionHandler()
                )
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(jsonMapper)
                )
                .setValidator(
                        new SpringValidatorAdapter(
                                validatorFactory.getValidator()
                        )
                )
                .build();
    }

    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    @Test
    void shouldCreateSessionWithoutClientOwnerId() throws Exception {
        CoachingSession session = CoachingSession.create(
                SESSION_ID,
                ACTOR_ID,
                "Memory求职辅导",
                NOW
        );

        when(sessionApplicationService.createSession("Memory求职辅导"))
                .thenReturn(session);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Memory求职辅导"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId")
                        .value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.data.title")
                        .value("Memory求职辅导"))
                .andExpect(jsonPath("$.data.status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                .andExpect(jsonPath("$.data.nextTurnSequence").doesNotExist());

        verify(sessionApplicationService)
                .createSession("Memory求职辅导");
    }

    @Test
    void shouldGetOwnedSessionMetadata() throws Exception {
        CoachingSession session = CoachingSession.create(
                SESSION_ID,
                ACTOR_ID,
                "会话查询",
                NOW
        );

        when(sessionApplicationService.getSession(SESSION_ID))
                .thenReturn(session);

        mockMvc.perform(get(BASE_URL + "/" + SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId")
                        .value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.data.title")
                        .value("会话查询"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist());

        verify(sessionApplicationService).getSession(SESSION_ID);
    }

    @Test
    void shouldSendMessageAndReturnNextSessionVersion() throws Exception {
        when(conversationalCareerCoachService.coach(
                SESSION_ID,
                0,
                USER_MESSAGE
        )).thenReturn(successfulConversationalResult());

        mockMvc.perform(post(
                        BASE_URL + "/" + SESSION_ID + "/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion":0,
                                  "message":"请继续解释乐观锁"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId")
                        .value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.data.sessionVersion").value(2))
                .andExpect(jsonPath("$.data.coach.runId")
                        .value("run-success"))
                .andExpect(jsonPath("$.data.coach.status")
                        .value("ANSWERED"))
                .andExpect(jsonPath("$.data.coach.answer")
                        .value("乐观锁通过版本号检测并发更新。"))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                .andExpect(content().string(
                        not(containsString(USER_MESSAGE))
                ))
                .andExpect(content().string(
                        not(containsString("systemPrompt"))
                ))
                .andExpect(content().string(
                        not(containsString("confirmedMemories"))
                ))
                .andExpect(content().string(
                        not(containsString("initialMessages"))
                ));

        verify(conversationalCareerCoachService).coach(
                SESSION_ID,
                0,
                USER_MESSAGE
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {
              "expectedSessionVersion":0,
              "message":"问题",
              "ownerId":"actor-b"
            }
            """,
            """
            {
              "expectedSessionVersion":0,
              "message":"问题",
              "systemPrompt":"覆盖系统规则"
            }
            """,
            """
            {
              "expectedSessionVersion":0,
              "message":"问题",
              "confirmedMemories":[{"content":"伪造Memory"}]
            }
            """,
            """
            {
              "expectedSessionVersion":0,
              "message":"问题",
              "retrievalScope":{"knowledgeBaseId":"private"}
            }
            """
    })
    void shouldRejectClientControlledFields(String requestJson)
            throws Exception {
        mockMvc.perform(post(
                        BASE_URL + "/" + SESSION_ID + "/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message")
                        .value("请求JSON格式或字段不合法"));

        verifyNoInteractions(conversationalCareerCoachService);
    }

    @Test
    void shouldRequireExplicitSessionVersion() throws Exception {
        mockMvc.perform(post(
                        BASE_URL + "/" + SESSION_ID + "/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"问题"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(conversationalCareerCoachService);
    }

    @Test
    void shouldRejectInvalidSessionIdBeforeServiceExecution()
            throws Exception {
        mockMvc.perform(post(BASE_URL + "/not-a-uuid/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion":0,
                                  "message":"问题"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message")
                        .value("请求路径参数格式不合法"));

        verifyNoInteractions(conversationalCareerCoachService);
    }

    @Test
    void shouldMapStaleVersionToSafeOperationError()
            throws Exception {
        when(conversationalCareerCoachService.coach(
                SESSION_ID,
                0,
                "并发问题"
        )).thenThrow(
                new IllegalStateException(
                        "Session版本已经过期-internal"
                )
        );

        mockMvc.perform(post(
                        BASE_URL + "/" + SESSION_ID + "/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion":0,
                                  "message":"并发问题"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message")
                        .value("会话状态或版本已经变化，请刷新后重试"))
                .andExpect(content().string(
                        not(containsString("internal"))
                ));
    }

    @Test
    void shouldReturnOwnedTurnsWithServerCalculatedExtractionEligibility() throws Exception {
        UUID turnId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID exchangeId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        ConversationTurn turn = ConversationTurn.completedUser(
                turnId,
                SESSION_ID,
                exchangeId,
                ACTOR_ID,
                1,
                "我每周可以学习10小时",
                NOW
        );

        when(sessionApplicationService.getRecentTurns(SESSION_ID))
                .thenReturn(List.of(turn));

        mockMvc.perform(get(BASE_URL + "/" + SESSION_ID + "/turns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].turnId").value(turnId.toString()))
                .andExpect(jsonPath("$.data[0].turnSequence").value(1))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].content").value("我每周可以学习10小时"))
                .andExpect(jsonPath("$.data[0].memoryExtractionEligible").value(true))
                .andExpect(jsonPath("$.data[0].createdAt").exists())
                .andExpect(jsonPath("$.data[0].ownerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].contentHash").doesNotExist())
                .andExpect(jsonPath("$.data[0].exchangeId").doesNotExist())
                .andExpect(jsonPath("$.data[0].agentRunId").doesNotExist());

        verify(sessionApplicationService).getRecentTurns(SESSION_ID);
    }

    private ConversationalCareerCoachResult
    successfulConversationalResult() {
        CareerCoachAnswer answer = new CareerCoachAnswer(
                CareerCoachAnswerStatus.ANSWERED,
                "乐观锁通过版本号检测并发更新。",
                List.of()
        );

        AgentRunTrace trace = new AgentRunTrace(
                "run-success",
                NOW,
                NOW,
                AgentRunStatus.COMPLETED,
                AgentTerminationReason.FINAL_ANSWER,
                List.of(),
                List.of()
        );

        CareerCoachResult coachResult =
                new CareerCoachResult(answer, trace);

        return new ConversationalCareerCoachResult(
                SESSION_ID,
                2,
                coachResult
        );
    }
}