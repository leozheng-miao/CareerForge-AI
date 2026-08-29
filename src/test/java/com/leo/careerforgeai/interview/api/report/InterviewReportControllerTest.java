package com.leo.careerforgeai.interview.api.report;

import com.leo.careerforgeai.interview.api.advice.InterviewReportApiExceptionHandler;
import com.leo.careerforgeai.interview.application.report.InterviewReportConfirmationFacade;
import com.leo.careerforgeai.interview.application.report.InterviewReportConfirmationFactory;
import com.leo.careerforgeai.interview.application.report.InterviewReportQueryApplicationService;
import com.leo.careerforgeai.interview.domain.InterviewReport;
import com.leo.careerforgeai.interview.domain.InterviewReportConfirmation;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
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
 * @description: 验证面试报告查询、逐项确认映射、安全响应和确认结果查询API
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
@ExtendWith(MockitoExtension.class)
class InterviewReportControllerTest {

    private static final UUID INTERVIEW_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000002");
    private static final UUID MEMORY_SUGGESTION_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000003");
    private static final UUID TRAINING_SUGGESTION_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000004");
    private static final UUID REQUEST_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000005");
    private static final UUID MEMORY_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000006");
    private static final UUID PLAN_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000007");
    private static final Instant NOW = Instant.parse("2026-08-30T08:00:00Z");

    @Mock
    private InterviewReportQueryApplicationService queryService;

    @Mock
    private InterviewReportConfirmationFacade confirmationFacade;

    private final ValidatorFactory validatorFactory =
            Validation.buildDefaultValidatorFactory();

    private MockMvc mockMvc;
    private InterviewReport report;
    private InterviewReportConfirmation confirmation;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        report = report();
        confirmation = appliedConfirmation(report);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new InterviewReportController(
                        queryService,
                        confirmationFacade
                ))
                .setControllerAdvice(
                        new InterviewReportApiExceptionHandler(),
                        new GlobalExceptionHandler()
                )
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(jsonMapper)
                )
                .setValidator(
                        new SpringValidatorAdapter(validatorFactory.getValidator())
                )
                .build();
    }

    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    @Test
    void shouldReturnReviewableReportWithoutOwnerOrModelInternalFields()
            throws Exception {
        when(queryService.getReport(INTERVIEW_ID)).thenReturn(report);

        mockMvc.perform(get(
                        "/api/mock-interviews/{interviewId}/report",
                        INTERVIEW_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reportId").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.data.interviewId").value(INTERVIEW_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.suggestions[0].suggestionId")
                        .value(MEMORY_SUGGESTION_ID.toString()))
                .andExpect(jsonPath("$.data.suggestions[0].type")
                        .value("MEMORY_CANDIDATE"))
                .andExpect(jsonPath("$.data.suggestions[0].skillName")
                        .value("Java并发"))
                .andExpect(jsonPath("$.data.suggestions[0].executable")
                        .value(true))
                .andExpect(jsonPath("$.data.suggestions[1].suggestionId")
                        .value(TRAINING_SUGGESTION_ID.toString()))
                .andExpect(jsonPath("$.data.suggestions[1].type")
                        .value("TRAINING_PLAN_ADJUSTMENT"))
                .andExpect(jsonPath("$.data.suggestions[1].focusArea")
                        .value("结构化并发"))
                .andExpect(jsonPath("$.data.suggestions[1].adjustment")
                        .value("增加结构化并发取消传播专项训练。"))
                .andExpect(content().string(not(containsString("ownerId"))))
                .andExpect(content().string(not(containsString("modelRequestId"))))
                .andExpect(content().string(not(containsString("inputHash"))))
                .andExpect(content().string(not(containsString("outputHash"))));

        verify(queryService).getReport(INTERVIEW_ID);
        verifyNoInteractions(confirmationFacade);
    }

    @Test
    void shouldMapCompleteDecisionsAndReturnQueryableApplicationResult()
            throws Exception {
        when(confirmationFacade.confirm(
                eq(INTERVIEW_ID),
                eq(REPORT_ID),
                eq(REQUEST_ID),
                eq(0L),
                anyList()
        )).thenReturn(confirmation);
        when(queryService.getConfirmation(INTERVIEW_ID, REPORT_ID))
                .thenReturn(confirmation);

        String requestJson = """
                {
                  "requestId":"81000000-0000-0000-0000-000000000005",
                  "expectedReportVersion":0,
                  "decisions":[
                    {
                      "suggestionId":"81000000-0000-0000-0000-000000000003",
                      "decisionType":"CONFIRMED"
                    },
                    {
                      "suggestionId":"81000000-0000-0000-0000-000000000004",
                      "decisionType":"CONFIRMED"
                    }
                  ]
                }
                """;

        mockMvc.perform(post(
                        "/api/mock-interviews/{interviewId}/reports/{reportId}/confirmation",
                        INTERVIEW_ID,
                        REPORT_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.data.decisions[0].applicationStatus")
                        .value("APPLIED"))
                .andExpect(jsonPath("$.data.decisions[0].outputReferenceId")
                        .value(MEMORY_ID.toString()))
                .andExpect(jsonPath("$.data.decisions[1].applicationStatus")
                        .value("APPLIED"))
                .andExpect(jsonPath("$.data.decisions[1].outputReferenceId")
                        .value(PLAN_ID.toString()))
                .andExpect(content().string(not(containsString("ownerId"))))
                .andExpect(content().string(not(containsString("requestFingerprint"))));

        verify(confirmationFacade).confirm(
                eq(INTERVIEW_ID),
                eq(REPORT_ID),
                eq(REQUEST_ID),
                eq(0L),
                argThat(selections ->
                        selections.size() == 2
                                && selections.getFirst().suggestionId()
                                .equals(MEMORY_SUGGESTION_ID)
                                && selections.getFirst().decisionType()
                                == InterviewReportConfirmation.DecisionType.CONFIRMED
                                && selections.get(1).suggestionId()
                                .equals(TRAINING_SUGGESTION_ID)
                )
        );

        mockMvc.perform(get(
                        "/api/mock-interviews/{interviewId}/reports/{reportId}/confirmation",
                        INTERVIEW_ID,
                        REPORT_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.decisions").isArray())
                .andExpect(jsonPath("$.data.decisions.length()").value(2));

        verify(queryService).getConfirmation(INTERVIEW_ID, REPORT_ID);
    }

    @Test
    void shouldRejectMissingVersionAndClientControlledFields()
            throws Exception {
        mockMvc.perform(post(
                        "/api/mock-interviews/{interviewId}/reports/{reportId}/confirmation",
                        INTERVIEW_ID,
                        REPORT_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId":"81000000-0000-0000-0000-000000000005",
                                  "decisions":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(post(
                        "/api/mock-interviews/{interviewId}/reports/{reportId}/confirmation",
                        INTERVIEW_ID,
                        REPORT_ID
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId":"81000000-0000-0000-0000-000000000005",
                                  "expectedReportVersion":0,
                                  "ownerId":"other-owner",
                                  "status":"APPLIED",
                                  "decisions":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message")
                        .value("请求JSON格式或字段不合法"));

        verifyNoInteractions(queryService, confirmationFacade);
    }

    private InterviewReport report() {
        InterviewReport.MemoryCandidatePayload memoryPayload =
                new InterviewReport.MemoryCandidatePayload(
                        "Java并发",
                        "保留Java并发实践作为长期能力证据。"
                );
        InterviewReport.TrainingPlanAdjustmentPayload trainingPayload =
                new InterviewReport.TrainingPlanAdjustmentPayload(
                        "结构化并发",
                        "增加结构化并发取消传播专项训练。"
                );

        return InterviewReport.pendingConfirmation(
                REPORT_ID,
                INTERVIEW_ID,
                new com.leo.careerforgeai.shared.actor.ActorId("api-owner"),
                List.of("能够解释虚拟线程适用边界。"),
                List.of("结构化并发取消传播理解不足。"),
                List.of(),
                List.of("补充取消传播实验。"),
                List.of(
                        new InterviewReport.Suggestion(
                                MEMORY_SUGGESTION_ID,
                                REPORT_ID,
                                INTERVIEW_ID,
                                new com.leo.careerforgeai.shared.actor.ActorId("api-owner"),
                                InterviewReport.SuggestionType.MEMORY_CANDIDATE,
                                1,
                                memoryPayload.content(),
                                memoryPayload,
                                "a".repeat(64),
                                NOW.minusSeconds(120)
                        ),
                        new InterviewReport.Suggestion(
                                TRAINING_SUGGESTION_ID,
                                REPORT_ID,
                                INTERVIEW_ID,
                                new com.leo.careerforgeai.shared.actor.ActorId("api-owner"),
                                InterviewReport.SuggestionType.TRAINING_PLAN_ADJUSTMENT,
                                1,
                                trainingPayload.adjustment(),
                                trainingPayload,
                                "b".repeat(64),
                                NOW.minusSeconds(120)
                        )
                ),
                "report-request",
                "report-coach-v2",
                "c".repeat(64),
                "d".repeat(64),
                NOW.minusSeconds(120)
        );
    }

    private InterviewReportConfirmation appliedConfirmation(
            InterviewReport sourceReport
    ) {
        InterviewReportConfirmationFactory factory =
                new InterviewReportConfirmationFactory(
                        JsonMapper.builder().build()
                );

        InterviewReportConfirmation current = factory.create(
                sourceReport,
                REQUEST_ID,
                sourceReport.version(),
                sourceReport.suggestions().stream()
                        .map(suggestion ->
                                new InterviewReportConfirmationFactory.Selection(
                                        suggestion.suggestionId(),
                                        InterviewReportConfirmation.DecisionType.CONFIRMED
                                ))
                        .toList(),
                NOW.minusSeconds(60)
        );

        List<InterviewReportConfirmation.Decision> originalDecisions =
                List.copyOf(current.decisions());

        current = current.recordDecision(
                originalDecisions.getFirst().applied(
                        MEMORY_ID,
                        NOW.minusSeconds(30)
                )
        );
        current = current.recordDecision(
                originalDecisions.get(1).applied(
                        PLAN_ID,
                        NOW.minusSeconds(20)
                )
        );
        return current.finish(null, NOW);
    }
}