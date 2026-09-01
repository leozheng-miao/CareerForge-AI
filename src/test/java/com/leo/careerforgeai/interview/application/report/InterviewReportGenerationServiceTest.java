package com.leo.careerforgeai.interview.application.report;

import com.leo.careerforgeai.interview.application.model.report.InterviewReportDraft;
import com.leo.careerforgeai.interview.application.model.report.InterviewReportInput;
import com.leo.careerforgeai.interview.application.model.report.InterviewReportSuggestionDraft;
import com.leo.careerforgeai.interview.application.model.report.InterviewReportRoleContract;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.session.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.execution.InterviewNodeExecution;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证报告执行权认领、Report Coach调用、报告持久化和失败收敛
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
class InterviewReportGenerationServiceTest {

    private static final ActorId OWNER = new ActorId("report-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EXISTING_REPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private InterviewReportPreparationService preparationService;
    private InterviewReportRoleContract reportContract;
    private InterviewRoleModelGateway modelGateway;
    private InterviewReportPersistenceService persistenceService;
    private InterviewReportGenerationService service;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        preparationService = mock(InterviewReportPreparationService.class);
        reportContract = mock(InterviewReportRoleContract.class);
        modelGateway = mock(InterviewRoleModelGateway.class);
        persistenceService = mock(InterviewReportPersistenceService.class);
        service = new InterviewReportGenerationService(
                preparationService,
                reportContract,
                modelGateway,
                persistenceService,
                new InterviewReportFactory(
                        jsonMapper,
                        new InterviewReportMemoryCandidatePolicy()
                ),
                jsonMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldClaimGenerateAndPersistReportOnce() {
        InterviewReportInput input = input();
        InterviewRoleModelGateway.Result<InterviewReportDraft> modelResult = modelResult();
        AtomicReference<InterviewNodeExecution> executionReference = new AtomicReference<>();

        when(preparationService.prepare(INTERVIEW_ID)).thenReturn(input);
        when(persistenceService.claimGeneration(eq(INTERVIEW_ID), anyString())).thenAnswer(invocation -> {
            String inputHash = invocation.getArgument(1);
            InterviewNodeExecution execution = InterviewNodeExecution.start(
                    EXECUTION_ID,
                    INTERVIEW_ID,
                    OWNER,
                    0,
                    InterviewReportPersistenceService.GENERATE_REPORT_NODE,
                    inputHash,
                    NOW
            );
            executionReference.set(execution);
            return new InterviewReportPersistenceService.Claim(null, execution);
        });
        when(modelGateway.generate(reportContract, input, TIMEOUT)).thenReturn(modelResult);
        when(persistenceService.persist(
                any(InterviewReport.class),
                any(InterviewNodeExecution.class),
                eq(modelResult)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        InterviewReport report = service.generateAndPersist(INTERVIEW_ID, TIMEOUT);

        assertThat(report.interviewId()).isEqualTo(INTERVIEW_ID);
        assertThat(report.ownerId()).isEqualTo(OWNER);
        assertThat(report.status()).isEqualTo(InterviewReport.Status.PENDING_CONFIRMATION);
        assertThat(report.inputHash()).isEqualTo(executionReference.get().inputHash());
        assertThat(report.strengths()).containsExactly("能够解释虚拟线程的适用边界。");
        assertThat(report.improvementActions()).containsExactly("补充结构化并发失败传播实验。");
        assertThat(report.suggestions())
                .extracting(InterviewReport.Suggestion::type)
                .containsExactly(
                        InterviewReport.SuggestionType.MEMORY_CANDIDATE,
                        InterviewReport.SuggestionType.TRAINING_PLAN_ADJUSTMENT
                );
        assertThat(report.suggestions().getFirst().payload())
                .isEqualTo(new InterviewReport.MemoryCandidatePayload(
                        "Java并发",
                        "保留Java并发实践作为长期能力证据。"
                ));
        assertThat(report.suggestions().get(1).payload())
                .isEqualTo(new InterviewReport.TrainingPlanAdjustmentPayload(
                        "结构化并发",
                        "增加结构化并发专项训练任务。"
                ));

        InOrder order = inOrder(preparationService, persistenceService, modelGateway);
        order.verify(preparationService).prepare(INTERVIEW_ID);
        order.verify(persistenceService).claimGeneration(eq(INTERVIEW_ID), anyString());
        order.verify(modelGateway).generate(reportContract, input, TIMEOUT);
        order.verify(persistenceService).persist(
                any(InterviewReport.class),
                eq(executionReference.get()),
                eq(modelResult)
        );
    }

    @Test
    void shouldReturnExistingReportWithoutCallingModelAgain() {
        InterviewReportInput input = input();
        InterviewReport existing = existingReport();

        when(preparationService.prepare(INTERVIEW_ID)).thenReturn(input);
        when(persistenceService.claimGeneration(eq(INTERVIEW_ID), anyString()))
                .thenReturn(new InterviewReportPersistenceService.Claim(existing, null));

        InterviewReport result = service.generateAndPersist(INTERVIEW_ID, TIMEOUT);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(modelGateway);
        verify(persistenceService, never()).persist(
                any(InterviewReport.class),
                any(InterviewNodeExecution.class),
                any()
        );
    }

    @Test
    void shouldMarkExecutionFailedWhenModelCallFails() {
        InterviewReportInput input = input();
        InterviewNodeExecution execution = InterviewNodeExecution.start(
                EXECUTION_ID,
                INTERVIEW_ID,
                OWNER,
                0,
                InterviewReportPersistenceService.GENERATE_REPORT_NODE,
                "a".repeat(64),
                NOW
        );
        IllegalStateException failure = new IllegalStateException("模拟Report Coach失败");

        when(preparationService.prepare(INTERVIEW_ID)).thenReturn(input);
        when(persistenceService.claimGeneration(eq(INTERVIEW_ID), anyString()))
                .thenReturn(new InterviewReportPersistenceService.Claim(null, execution));
        when(modelGateway.generate(reportContract, input, TIMEOUT)).thenThrow(failure);

        assertThatThrownBy(() -> service.generateAndPersist(INTERVIEW_ID, TIMEOUT)).isSameAs(failure);

        verify(persistenceService).fail(execution, InterviewFailureCode.INTERNAL_ERROR);
        verify(persistenceService, never()).persist(
                any(InterviewReport.class),
                any(InterviewNodeExecution.class),
                any()
        );
    }

    private InterviewReportInput input() {
        return new InterviewReportInput(
                INTERVIEW_ID,
                "目标岗位：Java AI应用开发工程师；核心要求：Java并发、Agent可靠性。",
                List.of(
                        "第1轮：虚拟线程回答基本正确；技术评分78；证据一致性通过。",
                        "第2轮：结构化并发取消传播说明不完整；技术评分62。"
                ),
                List.of("能够解释虚拟线程的适用边界。"),
                List.of(new InterviewReportInput.AllowedMemoryCandidate(
                        "Java并发",
                        "保留Java并发实践作为长期能力证据。"
                )),
                true
        );
    }
    private InterviewRoleModelGateway.Result<InterviewReportDraft> modelResult() {
        InterviewReportDraft draft = new InterviewReportDraft(
                List.of("能够解释虚拟线程的适用边界。"),
                List.of("结构化并发的取消传播理解不足。"),
                List.of("项目性能收益缺少可复现实验数据。"),
                List.of("补充结构化并发失败传播实验。"),
                List.of(new InterviewReportSuggestionDraft.MemoryCandidate(
                        "Java并发",
                        "保留Java并发实践作为长期能力证据。"
                )),
                List.of(new InterviewReportSuggestionDraft.TrainingPlanAdjustment(
                        "结构化并发",
                        "增加结构化并发专项训练任务。"
                ))
        );
        return new InterviewRoleModelGateway.Result<>(
                draft,
                "report-request-1",
                "deepseek-v4-flash",
                "report-coach-v2",
                new ModelUsage(600, 200, 800),
                2500,
                1,
                false,
                "b".repeat(64)
        );
    }
    private InterviewReport existingReport() {
        return InterviewReport.pendingConfirmation(
                EXISTING_REPORT_ID,
                INTERVIEW_ID,
                OWNER,
                List.of("已有优势"),
                List.of(),
                List.of(),
                List.of("已有改进动作"),
                List.of(),
                "existing-request",
                "report-coach-v2",
                "c".repeat(64),
                "d".repeat(64),
                NOW
        );
    }
}