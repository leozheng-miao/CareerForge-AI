package com.leo.careerforgeai.memory.api;

import com.leo.careerforgeai.memory.api.advice.MemoryCandidateApiExceptionHandler;
import com.leo.careerforgeai.memory.application.extraction.MemoryCandidateApplicationResult;
import com.leo.careerforgeai.memory.application.extraction.MemoryCandidateApplicationService;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionErrorType;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionException;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionFailureStage;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
import com.leo.careerforgeai.model.domain.ModelUsage;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
 * @description: 验证Memory候选提取API的字段白名单、响应脱敏和安全失败语义
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
@ExtendWith(MockitoExtension.class)
class MemoryCandidateControllerTest {

    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TURN_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MEMORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-13T04:00:00Z");
    private static final String URL = "/api/coaching-sessions/" + SESSION_ID
            + "/memory-candidate-extractions";

    @Mock
    private MemoryCandidateApplicationService applicationService;

    private final ValidatorFactory validatorFactory =
            Validation.buildDefaultValidatorFactory();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new MemoryCandidateController(applicationService))
                .setControllerAdvice(
                        new MemoryCandidateApiExceptionHandler(),
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
    void shouldExtractCandidatesWithoutExposingOwnerOrInternalModelMetadata() throws Exception {
        when(applicationService.extract(SESSION_ID, List.of(TURN_ID)))
                .thenReturn(applicationResult());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnIds":["20000000-0000-0000-0000-000000000001"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].memoryId").value(MEMORY_ID.toString()))
                .andExpect(jsonPath("$.data[0].type").value("TIME_CONSTRAINT"))
                .andExpect(jsonPath("$.data[0].normalizedKey").value("weekly_hours"))
                .andExpect(jsonPath("$.data[0].content").value("我每周可以学习10小时"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].sourceType").value("CONVERSATION_TURN"))
                .andExpect(jsonPath("$.data[0].sourceId").value(TURN_ID.toString()))
                .andExpect(jsonPath("$.data[0].extractionConfidence").value(0.90))
                .andExpect(jsonPath("$.data[0].version").value(0))
                .andExpect(content().string(not(containsString("ownerId"))))
                .andExpect(content().string(not(containsString("contentHash"))))
                .andExpect(content().string(not(containsString("sourceHash"))))
                .andExpect(content().string(not(containsString("modelRequestId"))))
                .andExpect(content().string(not(containsString("sourceAgentRunId"))))
                .andExpect(content().string(not(containsString("modelUsage"))));

        verify(applicationService).extract(SESSION_ID, List.of(TURN_ID));
    }

    @Test
    void shouldRejectEmptyTurnSelectionBeforeApplicationService() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnIds":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectClientControlledOwnerAndStatusFields() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnIds":["20000000-0000-0000-0000-000000000001"],
                                  "ownerId":"actor-b",
                                  "status":"CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求JSON格式或字段不合法"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldReturnSafeErrorWhenModelExtractionFails() throws Exception {
        when(applicationService.extract(SESSION_ID, List.of(TURN_ID)))
                .thenThrow(new MemoryExtractionException(
                        MemoryExtractionErrorType.MODEL_OUTPUT_INVALID,
                        MemoryExtractionFailureStage.SOURCE_REFERENCE_VALIDATION,
                        "模型返回了无法追溯的来源ID",
                        null,
                        "model-request-secret",
                        new ModelUsage(80, 20, 100),
                        35,
                        1
                ));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnIds":["20000000-0000-0000-0000-000000000001"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message")
                        .value("Memory候选提取失败，请稍后重试"))
                .andExpect(content().string(not(containsString("无法追溯"))))
                .andExpect(content().string(not(containsString("model-request-secret"))));

        verify(applicationService).extract(SESSION_ID, List.of(TURN_ID));
    }

    private MemoryCandidateApplicationResult applicationResult() {
        MemoryItem candidate = MemoryItem.createExtractedPending(
                MEMORY_ID,
                new ActorId("actor-a"),
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                "我每周可以学习10小时",
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        TURN_ID.toString(),
                        "c4a4b8e3a535f9319ac9dff9e655c65436427c950f9cc9355984d8c1df856031"
                ),
                "memory-request-1",
                new BigDecimal("0.90"),
                null,
                List.of(TURN_ID.toString()),
                NOW
        );

        return new MemoryCandidateApplicationResult(
                List.of(candidate),
                "memory-request-1",
                new ModelUsage(100, 30, 130),
                25,
                1,
                false
        );
    }
}