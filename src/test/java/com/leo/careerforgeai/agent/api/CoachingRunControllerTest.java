package com.leo.careerforgeai.agent.api;

import com.leo.careerforgeai.agent.api.advice.CareerCoachApiExceptionHandler;
import com.leo.careerforgeai.agent.application.run.CoachingRunApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAsyncSubmissionApplicationService;
import com.leo.careerforgeai.agent.application.run.CoachingRunNotFoundException;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunRequestConflictException;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunCapacityRejectedException;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @program: CareerForge-AI
 * @description: 验证Coaching Run异步受理、查询、响应脱敏、冲突和容量拒绝映射
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunControllerTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_TURN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T07:00:00Z");
    private static final String BASE_URL = "/api/coaching-runs";
    private static final String MESSAGE = "请解释Java并发";

    @Mock
    private CoachingRunAsyncSubmissionApplicationService submissionService;

    @Mock
    private CoachingRunApplicationService applicationService;

    private final ValidatorFactory validatorFactory =
            Validation.buildDefaultValidatorFactory();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CoachingRunController(
                                submissionService,
                                applicationService
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
    void shouldSubmitRunAndReturnHttp202Accepted() throws Exception {
        when(submissionService.submit(
                SESSION_ID,
                REQUEST_ID,
                4L,
                MESSAGE
        )).thenReturn(acceptedRun());

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runId").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.userTurnId").value(USER_TURN_ID.toString()))
                .andExpect(jsonPath("$.data.assistantTurnId").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                .andExpect(jsonPath("$.data.requestFingerprint").doesNotExist())
                .andExpect(content().string(not(containsString(MESSAGE))));

        verify(submissionService).submit(
                SESSION_ID,
                REQUEST_ID,
                4L,
                MESSAGE
        );
    }

    @Test
    void shouldQueryOwnedRun() throws Exception {
        when(applicationService.get(RUN_ID)).thenReturn(succeededRun());

        mockMvc.perform(get(BASE_URL + "/" + RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runId").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                .andExpect(jsonPath("$.data.requestFingerprint").doesNotExist());
    }

    @Test
    void shouldReturnHttp409ForRequestFingerprintConflict() throws Exception {
        when(submissionService.submit(
                SESSION_ID,
                REQUEST_ID,
                4L,
                MESSAGE
        )).thenThrow(new CoachingRunRequestConflictException(RUN_ID));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.message").value("requestId已被用于不同请求"));
    }

    @Test
    void shouldReturnHttp429WhenRunCapacityIsFull() throws Exception {
        when(submissionService.submit(
                SESSION_ID,
                REQUEST_ID,
                4L,
                MESSAGE
        )).thenThrow(new CoachingRunCapacityRejectedException(OWNER));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900))
                .andExpect(jsonPath("$.message").value("当前Run执行容量已满，请稍后重试"));
    }

    @Test
    void shouldReturnHttp404ForMissingOrCrossOwnerRun() throws Exception {
        when(applicationService.get(RUN_ID))
                .thenThrow(new CoachingRunNotFoundException(RUN_ID));

        mockMvc.perform(get(BASE_URL + "/" + RUN_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("Run不存在或不属于当前用户"));
    }

    private String validRequest() {
        return """
                {
                  "sessionId":"10000000-0000-0000-0000-000000000001",
                  "requestId":"20000000-0000-0000-0000-000000000001",
                  "expectedSessionVersion":4,
                  "message":"请解释Java并发"
                }
                """;
    }

    private CoachingRun acceptedRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                "a".repeat(64),
                4L,
                NOW
        ).accept(USER_TURN_ID, NOW.plusSeconds(1));
    }

    private CoachingRun succeededRun() {
        return acceptedRun()
                .start(NOW.plusSeconds(2))
                .succeed(ASSISTANT_TURN_ID, NOW.plusSeconds(3));
    }
}