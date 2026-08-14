package com.leo.careerforgeai.memory.api;

import com.leo.careerforgeai.memory.api.advice.MemoryDecisionApiExceptionHandler;
import com.leo.careerforgeai.memory.application.profile.MemoryDecisionApplicationService;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
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
 * @description: 验证Memory确认和拒绝API的输入白名单、状态响应、版本要求、撤销和安全失败语义
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@ExtendWith(MockitoExtension.class)
class MemoryDecisionControllerTest {

    private static final ActorId ACTOR_ID = new ActorId("actor-a");
    private static final UUID MEMORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TURN_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REPLACEMENT_MEMORY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-14T04:00:00Z");
    private static final String BASE_URL = "/api/memories/" + MEMORY_ID;

    @Mock
    private MemoryDecisionApplicationService decisionService;

    private final ValidatorFactory validatorFactory =
            Validation.buildDefaultValidatorFactory();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new MemoryDecisionController(decisionService))
                .setControllerAdvice(
                        new MemoryDecisionApiExceptionHandler(),
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
    void shouldConfirmCandidateWithoutExposingInternalFields() throws Exception {
        when(decisionService.confirm(MEMORY_ID, 0, "用户确认"))
                .thenReturn(decidedMemory(MemoryDecisionType.CONFIRM));

        mockMvc.perform(post(BASE_URL + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion":0,
                                  "note":"用户确认"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.memoryId").value(MEMORY_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(content().string(not(containsString("ownerId"))))
                .andExpect(content().string(not(containsString("contentHash"))))
                .andExpect(content().string(not(containsString("sourceHash"))))
                .andExpect(content().string(not(containsString("extractionModelRequestId"))))
                .andExpect(content().string(not(containsString("sourceAgentRunId"))));

        verify(decisionService).confirm(MEMORY_ID, 0, "用户确认");
    }

    @Test
    void shouldRejectCandidate() throws Exception {
        when(decisionService.reject(MEMORY_ID, 0, "用户忽略"))
                .thenReturn(decidedMemory(MemoryDecisionType.REJECT));

        mockMvc.perform(post(BASE_URL + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion":0,
                                  "note":"用户忽略"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.version").value(1));

        verify(decisionService).reject(MEMORY_ID, 0, "用户忽略");
    }

    @Test
    void shouldRequireExpectedVersion() throws Exception {
        mockMvc.perform(post(BASE_URL + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note":"缺少版本"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(decisionService);
    }

    @Test
    void shouldRejectClientControlledOwnerStatusAndDecisionType() throws Exception {
        mockMvc.perform(post(BASE_URL + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion":0,
                                  "ownerId":"actor-b",
                                  "status":"CONFIRMED",
                                  "decisionType":"CONFIRM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求JSON格式或字段不合法"));

        verifyNoInteractions(decisionService);
    }

    @Test
    void shouldReturnSafeVersionConflict() throws Exception {
        when(decisionService.confirm(MEMORY_ID, 0, null))
                .thenThrow(new IllegalStateException("Memory版本已经过期"));

        mockMvc.perform(post(BASE_URL + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion":0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value("Memory版本已经过期"));

        verify(decisionService).confirm(MEMORY_ID, 0, null);
    }

    @Test
    void shouldRejectInvalidMemoryId() throws Exception {
        mockMvc.perform(post("/api/memories/not-a-uuid/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion":0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求路径参数格式不合法"));

        verifyNoInteractions(decisionService);
    }

    @Test
    void shouldConfirmExplicitReplacement() throws Exception {
        when(decisionService.confirmReplacement(
                MEMORY_ID,
                1,
                REPLACEMENT_MEMORY_ID,
                0,
                "时间约束发生变化"
        )).thenReturn(confirmedReplacementMemory());

        mockMvc.perform(post(BASE_URL + "/replace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "replacementMemoryId":"30000000-0000-0000-0000-000000000002",
                              "expectedExistingVersion":1,
                              "expectedReplacementVersion":0,
                              "note":"时间约束发生变化"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.memoryId").value(REPLACEMENT_MEMORY_ID.toString()))
                .andExpect(jsonPath("$.data.supersedesId").value(MEMORY_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.version").value(1));

        verify(decisionService).confirmReplacement(
                MEMORY_ID,
                1,
                REPLACEMENT_MEMORY_ID,
                0,
                "时间约束发生变化"
        );
    }

    @Test
    void shouldRequireBothReplacementVersions() throws Exception {
        mockMvc.perform(post(BASE_URL + "/replace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "replacementMemoryId":"30000000-0000-0000-0000-000000000002",
                              "expectedExistingVersion":1
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(decisionService);
    }

    @Test
    void shouldRevokeConfirmedMemory() throws Exception {
        when(decisionService.revoke(MEMORY_ID, 1, "用户撤销"))
                .thenReturn(revokedMemory());

        mockMvc.perform(post(BASE_URL + "/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion":1,
                              "note":"用户撤销"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.memoryId").value(MEMORY_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.version").value(2));

        verify(decisionService).revoke(MEMORY_ID, 1, "用户撤销");
    }

    private MemoryItem revokedMemory() {
        MemoryItem confirmed = decidedMemory(MemoryDecisionType.CONFIRM);
        MemoryDecision revokeDecision = MemoryDecision.create(
                UUID.randomUUID(),
                confirmed,
                ACTOR_ID,
                MemoryDecisionType.REVOKE,
                null,
                "用户撤销",
                NOW.plusSeconds(2)
        );
        return confirmed.applyDecision(revokeDecision);
    }

    private MemoryItem decidedMemory(MemoryDecisionType decisionType) {
        MemoryItem candidate = MemoryItem.createExtractedPending(
                MEMORY_ID,
                ACTOR_ID,
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                "我每周可以学习10小时",
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        TURN_ID.toString(),
                        "a".repeat(64)
                ),
                "model-request-1",
                new BigDecimal("0.90"),
                null,
                List.of(TURN_ID.toString()),
                NOW
        );

        MemoryDecision decision = MemoryDecision.create(
                UUID.randomUUID(),
                candidate,
                ACTOR_ID,
                decisionType,
                null,
                "",
                NOW.plusSeconds(1)
        );

        return candidate.applyDecision(decision);
    }

    private MemoryItem confirmedReplacementMemory() {
        MemoryItem existingMemory = decidedMemory(MemoryDecisionType.CONFIRM);
        MemorySource source = new MemorySource(
                MemorySourceType.CONVERSATION_TURN,
                TURN_ID.toString(),
                "b".repeat(64)
        );
        MemoryItem replacementCandidate = MemoryItem.createExtractedPendingReplacement(
                REPLACEMENT_MEMORY_ID,
                existingMemory,
                "我每周可以学习6小时",
                source,
                "model-request-2",
                new BigDecimal("0.95"),
                null,
                List.of(TURN_ID.toString()),
                NOW.plusSeconds(2)
        );
        MemoryDecision decision = MemoryDecision.create(
                UUID.randomUUID(),
                replacementCandidate,
                ACTOR_ID,
                MemoryDecisionType.CONFIRM,
                null,
                "时间约束发生变化",
                NOW.plusSeconds(3)
        );
        return replacementCandidate.applyDecision(decision);
    }
}