package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.api.advice.InterviewFlowApiExceptionHandler;
import com.leo.careerforgeai.interviewflow.application.session.CreateQuickInterviewCommand;
import com.leo.careerforgeai.interviewflow.application.session.InterviewFlowNotFoundException;
import com.leo.careerforgeai.interviewflow.application.session.InterviewInProgressException;
import com.leo.careerforgeai.interviewflow.application.session.InterviewSessionApplicationService;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
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

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @program: CareerForge-AI
 * @description: 验证快速面试会话REST契约和安全冲突载荷
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@ExtendWith(MockitoExtension.class)
class InterviewSessionControllerTest {

    private static final UUID INTERVIEW_ID =
            UUID.fromString("32000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID =
            UUID.fromString("32000000-0000-0000-0000-000000000002");
    private static final ActorId OWNER = new ActorId("quick-owner-a");

    @Mock
    private InterviewSessionApplicationService service;

    private final ValidatorFactory validatorFactory =
            Validation.buildDefaultValidatorFactory();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InterviewSessionController(service))
                .setControllerAdvice(
                        new InterviewFlowApiExceptionHandler(),
                        new GlobalExceptionHandler()
                )
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(jsonMapper)
                )
                .setValidator(new SpringValidatorAdapter(
                        validatorFactory.getValidator()
                ))
                .build();
    }

    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    @Test
    void shouldCreateQuickInterviewAndReturnPreparingState() throws Exception {
        InterviewSession session = new InterviewSession(
                INTERVIEW_ID,
                OWNER,
                InterviewMode.QUICK,
                SessionStage.PREPARING,
                0
        );
        when(service.createQuick(any())).thenReturn(session);

        mockMvc.perform(post("/api/interviews/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "direction": "AI_AGENT_DEVELOPMENT"
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.interviewId")
                        .value(INTERVIEW_ID.toString()))
                .andExpect(jsonPath("$.data.mode").value("QUICK"))
                .andExpect(jsonPath("$.data.stage").value("PREPARING"))
                .andExpect(jsonPath("$.data.version").value(0));

        verify(service).createQuick(new CreateQuickInterviewCommand(
                REQUEST_ID,
                CreateQuickInterviewCommand.AI_AGENT_DEVELOPMENT
        ));
    }

    @Test
    void shouldMapCrossOwnerLookupToActualHttp404() throws Exception {
        when(service.get(INTERVIEW_ID))
                .thenThrow(new InterviewFlowNotFoundException(INTERVIEW_ID));

        mockMvc.perform(get("/api/interviews/{interviewId}", INTERVIEW_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void shouldReturnActual409WithOnlySafeRecoveryFields() throws Exception {
        var recovery = new InterviewSessionApplicationService.InProgressInterview(
                INTERVIEW_ID,
                InterviewMode.QUICK,
                3,
                3510
        );
        when(service.enter(INTERVIEW_ID, REQUEST_ID, 1))
                .thenThrow(new InterviewInProgressException(recovery));

        mockMvc.perform(post(
                            "/api/interviews/{interviewId}/enter",
                            INTERVIEW_ID
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "expectedVersion": 1
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.data.conflictCode")
                        .value("INTERVIEW_IN_PROGRESS"))
                .andExpect(jsonPath("$.data.interviewId")
                        .value(INTERVIEW_ID.toString()))
                .andExpect(jsonPath("$.data.mode").value("QUICK"))
                .andExpect(jsonPath("$.data.currentQuestionNumber").value(3))
                .andExpect(jsonPath("$.data.remainingSeconds").value(3510))
                .andExpect(jsonPath("$.data.answer").doesNotExist())
                .andExpect(jsonPath("$.data.analysis").doesNotExist());
    }

    @Test
    void shouldExposeTheCurrentOwnersResumableInterview() throws Exception {
        var recovery = new InterviewSessionApplicationService.InProgressInterview(
                INTERVIEW_ID,
                InterviewMode.QUICK,
                1,
                3600
        );
        when(service.findInProgress()).thenReturn(Optional.of(recovery));

        mockMvc.perform(get("/api/interviews/resumable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.interviewId")
                        .value(INTERVIEW_ID.toString()))
                .andExpect(jsonPath("$.data.currentQuestionNumber").value(1));
    }
}
