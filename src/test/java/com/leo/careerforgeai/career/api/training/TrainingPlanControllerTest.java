package com.leo.careerforgeai.career.api.training;

import com.leo.careerforgeai.career.api.advice.TrainingPlanApiExceptionHandler;
import com.leo.careerforgeai.career.application.training.*;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
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
import java.util.List;
import java.util.UUID;

import static com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType.MODEL_OUTPUT_INVALID;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @program: CareerForge-AI
 * @description: 验证训练计划生成与查询API的字段白名单、待确认状态和安全失败语义
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
@ExtendWith(MockitoExtension.class)
class TrainingPlanControllerTest {

    private static final UUID SNAPSHOT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID MEMORY_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-18T06:00:00Z");

    @Mock
    private TrainingPlanGenerationApplicationService generationService;

    @Mock
    private TrainingPlanApplicationService planService;

    private final ValidatorFactory validatorFactory =
            Validation.buildDefaultValidatorFactory();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TrainingPlanController(generationService, planService))
                .setControllerAdvice(new TrainingPlanApiExceptionHandler(), new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .setValidator(new SpringValidatorAdapter(validatorFactory.getValidator()))
                .build();
    }

    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    @Test
    void shouldGeneratePendingConfirmationPlanWithoutInternalMetadata() throws Exception {
        when(generationService.generate(SNAPSHOT_ID)).thenReturn(pendingPlan());

        mockMvc.perform(post("/api/training-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gapSnapshotId":"20000000-0000-0000-0000-000000000001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planId").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.data.planVersion").value(1))
                .andExpect(jsonPath("$.data.gapSnapshotId").value(SNAPSHOT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.durationWeeks").value(1))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.items[0].itemId").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.items[0].gapItemIds[0]").value(GAP_ITEM_ID.toString()))
                .andExpect(content().string(not(containsString("ownerId"))))
                .andExpect(content().string(not(containsString("generationContext"))))
                .andExpect(content().string(not(containsString("contentHash"))))
                .andExpect(content().string(not(containsString("sourceHash"))))
                .andExpect(content().string(not(containsString("modelRequestId"))))
                .andExpect(content().string(not(containsString("inputTokens"))));

        verify(generationService).generate(SNAPSHOT_ID);
        verifyNoInteractions(planService);
    }

    @Test
    void shouldQueryOwnedPlanById() throws Exception {
        when(planService.get(PLAN_ID)).thenReturn(pendingPlan());

        mockMvc.perform(get("/api/training-plans/{planId}", PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planId").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.items[0].status").value("NOT_STARTED"));

        verify(planService).get(PLAN_ID);
        verifyNoInteractions(generationService);
    }

    @Test
    void shouldRejectClientControlledOwnerStatusAndVersion() throws Exception {
        mockMvc.perform(post("/api/training-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gapSnapshotId":"20000000-0000-0000-0000-000000000001",
                                  "ownerId":"actor-b",
                                  "status":"ACTIVE",
                                  "planVersion":99
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求JSON格式或字段不合法"));

        verifyNoInteractions(generationService, planService);
    }

    @Test
    void shouldHideInvalidModelOutputDetails() throws Exception {
        when(generationService.generate(SNAPSHOT_ID)).thenThrow(
                new TrainingPlanGenerationException(
                        MODEL_OUTPUT_INVALID,
                        "模型引用了secret-resource并伪造用户证书"
                )
        );

        mockMvc.perform(post("/api/training-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gapSnapshotId":"20000000-0000-0000-0000-000000000001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value("训练计划草案未通过安全校验，请重新生成"))
                .andExpect(content().string(not(containsString("secret-resource"))))
                .andExpect(content().string(not(containsString("用户证书"))));
    }

    @Test
    void shouldRejectMalformedPlanId() throws Exception {
        mockMvc.perform(get("/api/training-plans/not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求路径参数格式不合法"));

        verifyNoInteractions(generationService, planService);
    }

    @Test
    void shouldConfirmPendingPlanAndReturnActivePlan() throws Exception {
        TrainingPlan activePlan = pendingPlan().activate(NOW.plusSeconds(1));
        when(planService.activate(PLAN_ID, 1)).thenReturn(activePlan);

        mockMvc.perform(post("/api/training-plans/{planId}/confirm", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion":1
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planId").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.activatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.items[0].completionEvidenceRefs").isEmpty());

        verify(planService).activate(PLAN_ID, 1);
        verifyNoInteractions(generationService);
    }

    @Test
    void shouldRejectClientControlledConfirmationState() throws Exception {
        mockMvc.perform(post("/api/training-plans/{planId}/confirm", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion":1,
                              "ownerId":"actor-b",
                              "status":"ACTIVE",
                              "activatedAt":"2026-08-18T06:00:00Z"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求JSON格式或字段不合法"));

        verifyNoInteractions(generationService, planService);
    }

    @Test
    void shouldRejectMissingExpectedVersionBeforeApplicationService() throws Exception {
        mockMvc.perform(post("/api/training-plans/{planId}/confirm", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(generationService, planService);
    }

    @Test
    void shouldReturnSafeVersionConflictForConfirmation() throws Exception {
        when(planService.activate(PLAN_ID, 1))
                .thenThrow(new TrainingPlanVersionConflictException("数据库中的实际version为2"));

        mockMvc.perform(post("/api/training-plans/{planId}/confirm", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion":1
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value("训练计划版本已经变化，请刷新后重试"))
                .andExpect(content().string(not(containsString("实际version为2"))));
    }

    @Test
    void shouldStartActivePlanItem() throws Exception {
        TrainingPlan active = pendingPlan().activate(NOW.plusSeconds(1));
        TrainingPlan started = active.startItem(ITEM_ID, NOW.plusSeconds(2));
        when(planService.startItem(PLAN_ID, 2, ITEM_ID)).thenReturn(started);

        mockMvc.perform(post("/api/training-plans/{planId}/items/{itemId}/start", PLAN_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion":2
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.items[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.items[0].version").value(1))
                .andExpect(jsonPath("$.data.items[0].completionEvidenceRefs").isEmpty());

        verify(planService).startItem(PLAN_ID, 2, ITEM_ID);
        verifyNoInteractions(generationService);
    }

    @Test
    void shouldCompleteItemOnlyWithUserEvidence() throws Exception {
        List<String> evidenceRefs = List.of(
                "github:careerforge-ai/commit/abc123",
                "test-report:training-item-1"
        );
        TrainingPlan active = pendingPlan().activate(NOW.plusSeconds(1));
        TrainingPlan started = active.startItem(ITEM_ID, NOW.plusSeconds(2));
        TrainingPlan itemCompleted = started.completeItem(
                ITEM_ID,
                evidenceRefs,
                NOW.plusSeconds(3)
        );
        when(planService.completeItem(PLAN_ID, 3, ITEM_ID, evidenceRefs))
                .thenReturn(itemCompleted);

        mockMvc.perform(post("/api/training-plans/{planId}/items/{itemId}/complete", PLAN_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion":3,
                              "evidenceRefs":[
                                "github:careerforge-ai/commit/abc123",
                                "test-report:training-item-1"
                              ]
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].version").value(2))
                .andExpect(jsonPath("$.data.items[0].completionEvidenceRefs[0]")
                        .value("github:careerforge-ai/commit/abc123"))
                .andExpect(jsonPath("$.data.items[0].completionEvidenceRefs[1]")
                        .value("test-report:training-item-1"));

        verify(planService).completeItem(PLAN_ID, 3, ITEM_ID, evidenceRefs);
    }

    @Test
    void shouldRejectEmptyCompletionEvidenceBeforeApplicationService() throws Exception {
        mockMvc.perform(post("/api/training-plans/{planId}/items/{itemId}/complete", PLAN_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion":3,
                              "evidenceRefs":[]
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(generationService, planService);
    }

    @Test
    void shouldRejectClientControlledItemCompletionState() throws Exception {
        mockMvc.perform(post("/api/training-plans/{planId}/items/{itemId}/complete", PLAN_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion":3,
                              "evidenceRefs":["github:commit/abc123"],
                              "status":"COMPLETED",
                              "completedAt":"2026-08-18T06:00:00Z",
                              "itemVersion":99
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求JSON格式或字段不合法"));

        verifyNoInteractions(generationService, planService);
    }

    @Test
    void shouldCompletePlanAfterAllItemsCompleted() throws Exception {
        List<String> evidenceRefs = List.of("github:careerforge-ai/commit/abc123");
        TrainingPlan active = pendingPlan().activate(NOW.plusSeconds(1));
        TrainingPlan started = active.startItem(ITEM_ID, NOW.plusSeconds(2));
        TrainingPlan itemCompleted = started.completeItem(ITEM_ID, evidenceRefs, NOW.plusSeconds(3));
        TrainingPlan completed = itemCompleted.complete(NOW.plusSeconds(4));
        when(planService.complete(PLAN_ID, 4)).thenReturn(completed);
        mockMvc.perform(post("/api/training-plans/{planId}/complete", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "expectedVersion":4
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.version").value(5))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"));

        verify(planService).complete(PLAN_ID, 4);
    }

    @Test
    void shouldRejectUncontrolledCompletionEvidenceRefWithStableApiError() throws Exception {
        List<String> evidenceRefs = List.of("开始第一次item测试");
        when(planService.completeItem(PLAN_ID, 3, ITEM_ID, evidenceRefs))
                .thenThrow(new IllegalArgumentException("完成证据引用格式不受支持"));

        mockMvc.perform(post("/api/training-plans/{planId}/items/{itemId}/complete", PLAN_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "expectedVersion":3,
                          "evidenceRefs":["开始第一次item测试"]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("完成证据引用格式不受支持"));

        verify(planService).completeItem(PLAN_ID, 3, ITEM_ID, evidenceRefs);
        verifyNoInteractions(generationService);
    }

    @Test
    void shouldCancelPendingPlan() throws Exception {
        TrainingPlan cancelled = pendingPlan().cancel(NOW.plusSeconds(1));
        when(planService.cancel(PLAN_ID, 1)).thenReturn(cancelled);

        mockMvc.perform(post("/api/training-plans/{planId}/cancel", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "expectedVersion":1
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].status").value("NOT_STARTED"));

        verify(planService).cancel(PLAN_ID, 1);
        verifyNoInteractions(generationService);
    }

    private TrainingPlan pendingPlan() {
        TrainingPlanItem item = TrainingPlanItem.createDraft(
                ITEM_ID,
                1,
                "完成Java并发训练",
                "实现并验证一个线程安全的任务处理器",
                120,
                "自动化测试覆盖成功和并发冲突场景",
                "提交代码仓库引用和测试结果",
                List.of(GAP_ITEM_ID),
                null,
                List.of(new TrainingPlanItem.ResourceRef(
                        TrainingPlanItem.ResourceType.KNOWLEDGE_DOCUMENT,
                        "document-1"
                )),
                NOW.minusSeconds(10)
        );

        TrainingPlan.GenerationContext context = new TrainingPlan.GenerationContext(
                "training-plan-generation-context-v1",
                "training-plan-input-v1",
                600,
                List.of(new TrainingPlan.MemoryInputRef(
                        MEMORY_ID,
                        1,
                        MemoryType.TIME_CONSTRAINT,
                        "weekly_hours",
                        "a".repeat(64)
                )),
                List.of(new TrainingPlan.ResourceInputRef(
                        TrainingPlanItem.ResourceType.KNOWLEDGE_DOCUMENT,
                        "document-1",
                        "b".repeat(64)
                )),
                "training-plan-generator-v1",
                "training-plan-prompt-v1",
                "secret-model-request",
                100,
                50,
                150,
                25
        );

        return TrainingPlan.createGeneratedDraft(
                PLAN_ID,
                new ActorId("actor-training-a"),
                1,
                SNAPSHOT_ID,
                context,
                "Java后端训练计划",
                List.of(item),
                NOW
        ).submitForConfirmation(NOW);
    }
}