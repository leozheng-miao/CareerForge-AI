package com.leo.careerforgeai.interviewflow.api.answer;

import com.leo.careerforgeai.interviewflow.api.advice.InterviewFlowApiExceptionHandler;
import com.leo.careerforgeai.interviewflow.application.answer.InterviewAnswerApplicationService;
import com.leo.careerforgeai.interviewflow.application.answer.InterviewAnswerApplicationService.DraftResult;
import com.leo.careerforgeai.interviewflow.application.answer.InterviewAnswerApplicationService.NextAction;
import com.leo.careerforgeai.interviewflow.application.answer.InterviewAnswerApplicationService.SubmissionResult;
import com.leo.careerforgeai.interviewflow.application.answer.InterviewAnswerVersionConflictException;
import com.leo.careerforgeai.interviewflow.application.answer.SaveDraftCommand;
import com.leo.careerforgeai.interviewflow.application.answer.SkipQuestionCommand;
import com.leo.careerforgeai.interviewflow.application.answer.SubmitAnswerCommand;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;
import com.leo.careerforgeai.interviewflow.domain.turn.AnswerStatus;
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

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @program: CareerForge-AI
 * @description: 验证快速面试草稿、提交和跳过REST契约
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@ExtendWith(MockitoExtension.class)
class InterviewAnswerControllerTest {

    private static final UUID INTERVIEW_ID =
            UUID.fromString("37000000-0000-0000-0000-000000000001");
    private static final UUID QUESTION_ID =
            UUID.fromString("37000000-0000-0000-0000-000000000002");
    private static final UUID ANSWER_ID =
            UUID.fromString("37000000-0000-0000-0000-000000000003");
    private static final UUID REQUEST_ID =
            UUID.fromString("37000000-0000-0000-0000-000000000004");

    @Mock
    private InterviewAnswerApplicationService service;

    private final ValidatorFactory validatorFactory =
            Validation.buildDefaultValidatorFactory();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InterviewAnswerController(service))
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
    void shouldSaveDraftThroughTheQuestionScopedEndpoint() throws Exception {
        when(service.saveDraft(new SaveDraftCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                "草稿内容",
                2
        ))).thenReturn(new DraftResult(
                INTERVIEW_ID,
                QUESTION_ID,
                ANSWER_ID,
                "草稿内容",
                AnswerStatus.DRAFT,
                3
        ));

        mockMvc.perform(put(
                            "/api/interviews/{interviewId}/questions/{questionId}/draft",
                            INTERVIEW_ID,
                            QUESTION_ID
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "草稿内容",
                                  "expectedVersion": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerId")
                        .value(ANSWER_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    void shouldSubmitAndReturnTheDurableNextAction() throws Exception {
        SubmitAnswerCommand command = new SubmitAnswerCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                REQUEST_ID,
                "正式回答",
                1
        );
        when(service.submit(command)).thenReturn(new SubmissionResult(
                INTERVIEW_ID,
                QUESTION_ID,
                ANSWER_ID,
                AnswerStatus.SUBMITTED,
                2,
                SessionStage.INTERVIEWING,
                TimerState.PAUSED,
                5,
                NextAction.ANALYZING
        ));

        mockMvc.perform(post(
                            "/api/interviews/{interviewId}/questions/{questionId}/submit",
                            INTERVIEW_ID,
                            QUESTION_ID
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "content": "正式回答",
                                  "expectedVersion": 1
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerStatus")
                        .value("SUBMITTED"))
                .andExpect(jsonPath("$.data.timerState").value("PAUSED"))
                .andExpect(jsonPath("$.data.nextAction")
                        .value("ANALYZING"));

        verify(service).submit(command);
    }

    @Test
    void shouldSkipAndRejectOversizedAnswerInput() throws Exception {
        SkipQuestionCommand command = new SkipQuestionCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                REQUEST_ID,
                0
        );
        when(service.skip(command)).thenReturn(new SubmissionResult(
                INTERVIEW_ID,
                QUESTION_ID,
                ANSWER_ID,
                AnswerStatus.DISCARDED,
                0,
                SessionStage.INTERVIEWING,
                TimerState.PAUSED,
                4,
                NextAction.ANALYZING
        ));

        mockMvc.perform(post(
                            "/api/interviews/{interviewId}/questions/{questionId}/skip",
                            INTERVIEW_ID,
                            QUESTION_ID
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerStatus")
                        .value("DISCARDED"));

        mockMvc.perform(put(
                            "/api/interviews/{interviewId}/questions/{questionId}/draft",
                            INTERVIEW_ID,
                            QUESTION_ID
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "%s",
                                  "expectedVersion": 0
                                }
                                """.formatted("a".repeat(8001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnCurrentServerVersionWhenDraftCasConflicts()
            throws Exception {
        SaveDraftCommand command = new SaveDraftCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                "过期草稿",
                1
        );
        when(service.saveDraft(command))
                .thenThrow(new InterviewAnswerVersionConflictException(3));

        mockMvc.perform(put(
                            "/api/interviews/{interviewId}/questions/{questionId}/draft",
                            INTERVIEW_ID,
                            QUESTION_ID
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "过期草稿",
                                  "expectedVersion": 1
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.currentVersion").value(3));
    }
}
