package com.leo.careerforgeai.interview.api.session;

import com.leo.careerforgeai.interview.api.advice.MockInterviewApiExceptionHandler;
import com.leo.careerforgeai.interview.api.controller.MockInterviewController;
import com.leo.careerforgeai.interview.application.execution.MockInterviewAsyncSubmissionApplicationService;
import com.leo.careerforgeai.interview.application.execution.MockInterviewReportRetryApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewCancellationConflictException;
import com.leo.careerforgeai.interview.application.session.MockInterviewCreationApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.web.GlobalExceptionHandler;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @program: CareerForge-AI
 * @description: 验证模拟面试异步启动、MySQL状态查询和启动参数校验API
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@ExtendWith(MockitoExtension.class)
class MockInterviewControllerTest {

    private static final UUID INTERVIEW_ID = UUID.fromString("92000000-0000-0000-0000-000000000001");
    private static final ActorId OWNER = new ActorId("owner-a");
    private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");

    @Mock
    private MockInterviewCreationApplicationService creationService;

    @Mock
    private MockInterviewAsyncSubmissionApplicationService asyncSubmissionService;

    @Mock
    private MockInterviewLifecycleApplicationService lifecycleService;

    @Mock
    private MockInterviewReportRetryApplicationService reportRetryService;

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new MockInterviewController(
                        creationService,
                        asyncSubmissionService,
                        reportRetryService,
                        lifecycleService
                ))
                .setControllerAdvice(
                        new MockInterviewApiExceptionHandler(),
                        new GlobalExceptionHandler()
                )
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .setValidator(new SpringValidatorAdapter(validatorFactory.getValidator()))
                .build();
    }

    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    @Test
    void shouldStartInterviewAndQueryLatestMysqlStatus() throws Exception {
        MockInterviewSession created = createdSession();
        MockInterviewSession generating = created.startQuestionGeneration(NOW.plusSeconds(1));
        MockInterviewSession waiting = generating.waitForAnswer(NOW.plusSeconds(2));

        when(asyncSubmissionService.submitStart(INTERVIEW_ID, 0)).thenReturn(generating);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(waiting);

        mockMvc.perform(post("/api/mock-interviews/{interviewId}/start", INTERVIEW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.interviewId").value(INTERVIEW_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("GENERATING_QUESTION"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get("/api/mock-interviews/{interviewId}", INTERVIEW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_ANSWER"))
                .andExpect(jsonPath("$.data.version").value(2));

        verify(asyncSubmissionService).submitStart(INTERVIEW_ID, 0);
        verify(lifecycleService).get(INTERVIEW_ID);
        verifyNoInteractions(creationService);
    }

    @Test
    void shouldRejectMissingExpectedVersionBeforeApplicationService() throws Exception {
        mockMvc.perform(post("/api/mock-interviews/{interviewId}/start", INTERVIEW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(asyncSubmissionService, lifecycleService, creationService);
    }

    @Test
    void shouldCancelInterviewAndMapTerminalConflict() throws Exception {
        MockInterviewSession waiting = createdSession()
                .startQuestionGeneration(NOW.plusSeconds(1))
                .waitForAnswer(NOW.plusSeconds(2));
        MockInterviewSession cancelled = waiting.cancel(NOW.plusSeconds(3));

        when(lifecycleService.cancel(INTERVIEW_ID, 2)).thenReturn(cancelled);
        when(lifecycleService.cancel(INTERVIEW_ID, 3))
                .thenThrow(new MockInterviewCancellationConflictException(
                        INTERVIEW_ID, InterviewStatus.COMPLETED
                ));

        mockMvc.perform(post("/api/mock-interviews/{interviewId}/cancel", INTERVIEW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion": 2
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.version").value(3));

        mockMvc.perform(post("/api/mock-interviews/{interviewId}/cancel", INTERVIEW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion": 3
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.message").value("当前面试已经进入COMPLETED状态，不能取消"));

        verify(lifecycleService).cancel(INTERVIEW_ID, 2);
        verify(lifecycleService).cancel(INTERVIEW_ID, 3);
        verifyNoInteractions(creationService, asyncSubmissionService);
    }

    private MockInterviewSession createdSession() {
        return MockInterviewSession.create(
                INTERVIEW_ID,
                OWNER,
                UUID.fromString("92000000-0000-0000-0000-000000000002"),
                "a".repeat(64),
                InterviewMode.TARGETED_MOCK,
                UUID.fromString("92000000-0000-0000-0000-000000000003"),
                "b".repeat(64),
                new InterviewBudgetPolicy(5, 2, 20, 20_000),
                NOW
        );
    }
}