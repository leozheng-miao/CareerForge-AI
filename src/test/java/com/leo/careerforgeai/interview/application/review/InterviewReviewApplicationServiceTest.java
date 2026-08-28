package com.leo.careerforgeai.interview.application.review;

import com.leo.careerforgeai.interview.application.model.contract.EvidenceReviewInput;
import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewDraft;
import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewInput;
import com.leo.careerforgeai.interview.application.model.validation.EvidenceReviewRoleContract;
import com.leo.careerforgeai.interview.application.model.validation.TechnicalReviewRoleContract;
import com.leo.careerforgeai.interview.application.port.InterviewNodeExecutionRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.EvidenceReview;
import com.leo.careerforgeai.interview.domain.EvidenceReviewSource;
import com.leo.careerforgeai.interview.domain.InterviewNodeExecution;
import com.leo.careerforgeai.interview.domain.InterviewNodeExecutionStatus;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.TechnicalReview;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证技术评审、Java证据跳过、幂等重放及失败节点重试
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewReviewApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("review-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ANSWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void shouldPersistTechnicalReviewSkipEvidenceModelAndReplayBothResults() {
        InterviewReviewPreparationService preparationService = mock(InterviewReviewPreparationService.class);
        InterviewReviewRepository reviewRepository = mock(InterviewReviewRepository.class);
        InterviewNodeExecutionRepository executionRepository = mock(InterviewNodeExecutionRepository.class);
        InterviewRoleModelGateway modelGateway = mock(InterviewRoleModelGateway.class);
        TechnicalReviewRoleContract technicalContract = mock(TechnicalReviewRoleContract.class);
        EvidenceReviewRoleContract evidenceContract = mock(EvidenceReviewRoleContract.class);
        var prepared = prepared(InterviewReviewPlan.TECHNICAL_ONLY);
        var technicalStored = new AtomicReference<TechnicalReview>();
        var evidenceStored = new AtomicReference<EvidenceReview>();
        Map<String, InterviewNodeExecution> executions = new ConcurrentHashMap<>();

        when(preparationService.prepare(INTERVIEW_ID, 1, QUESTION_ID, ANSWER_ID))
                .thenReturn(prepared);
        configureRepositories(
                reviewRepository,
                executionRepository,
                executions,
                technicalStored,
                evidenceStored
        );

        InterviewRoleModelGateway.Result<TechnicalReviewDraft> modelResult = technicalResult();
        doReturn(modelResult).when(modelGateway).generate(
                technicalContract,
                prepared.technicalInput(),
                TIMEOUT
        );

        InterviewReviewPersistenceService persistenceService =
                new InterviewReviewPersistenceService(
                        reviewRepository,
                        executionRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );
        InterviewReviewApplicationService service = new InterviewReviewApplicationService(
                preparationService,
                persistenceService,
                reviewRepository,
                executionRepository,
                modelGateway,
                technicalContract,
                evidenceContract,
                JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        TechnicalReview technical = service.reviewTechnical(
                INTERVIEW_ID, 1, QUESTION_ID, ANSWER_ID, TIMEOUT
        );
        TechnicalReview technicalReplay = service.reviewTechnical(
                INTERVIEW_ID, 1, QUESTION_ID, ANSWER_ID, TIMEOUT
        );
        EvidenceReview evidence = service.reviewEvidence(
                INTERVIEW_ID, 1, QUESTION_ID, ANSWER_ID, TIMEOUT
        );
        EvidenceReview evidenceReplay = service.reviewEvidence(
                INTERVIEW_ID, 1, QUESTION_ID, ANSWER_ID, TIMEOUT
        );

        assertThat(technicalReplay).isSameAs(technical);
        assertThat(technical.dimensionScores()).containsEntry("TECHNICAL_CORRECTNESS", 4);
        assertThat(evidenceReplay).isSameAs(evidence);
        assertThat(evidence.source()).isEqualTo(EvidenceReviewSource.JAVA);
        assertThat(evidence.evidenceReferenceIds()).isEmpty();
        assertThat(executions.values())
                .allMatch(execution -> execution.status() == InterviewNodeExecutionStatus.SUCCEEDED);
        assertThat(executions.get(key(InterviewReviewApplicationService.TECHNICAL_NODE,
                technical.inputHash())).modelCallCount()).isEqualTo(1);
        assertThat(executions.get(key(InterviewReviewApplicationService.EVIDENCE_NODE,
                evidence.inputHash())).modelCallCount()).isZero();

        verify(modelGateway, times(1)).generate(any(), any(), any());
    }

    @Test
    void shouldRetryFailedTechnicalNodeWithoutCreatingDuplicateReview() {
        InterviewReviewPreparationService preparationService = mock(InterviewReviewPreparationService.class);
        InterviewReviewRepository reviewRepository = mock(InterviewReviewRepository.class);
        InterviewNodeExecutionRepository executionRepository = mock(InterviewNodeExecutionRepository.class);
        InterviewRoleModelGateway modelGateway = mock(InterviewRoleModelGateway.class);
        TechnicalReviewRoleContract technicalContract = mock(TechnicalReviewRoleContract.class);
        EvidenceReviewRoleContract evidenceContract = mock(EvidenceReviewRoleContract.class);
        var prepared = prepared(InterviewReviewPlan.TECHNICAL_ONLY);
        var technicalStored = new AtomicReference<TechnicalReview>();
        var evidenceStored = new AtomicReference<EvidenceReview>();
        Map<String, InterviewNodeExecution> executions = new ConcurrentHashMap<>();

        when(preparationService.prepare(INTERVIEW_ID, 1, QUESTION_ID, ANSWER_ID))
                .thenReturn(prepared);
        configureRepositories(
                reviewRepository,
                executionRepository,
                executions,
                technicalStored,
                evidenceStored
        );

        doThrow(new ModelException(ModelErrorType.TIMEOUT, "模型调用超时"))
                .doReturn(technicalResult())
                .when(modelGateway)
                .generate(technicalContract, prepared.technicalInput(), TIMEOUT);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InterviewReviewPersistenceService persistenceService =
                new InterviewReviewPersistenceService(
                        reviewRepository,
                        executionRepository,
                        clock
                );
        InterviewReviewApplicationService service = new InterviewReviewApplicationService(
                preparationService,
                persistenceService,
                reviewRepository,
                executionRepository,
                modelGateway,
                technicalContract,
                evidenceContract,
                JsonMapper.builder().build(),
                clock
        );

        assertThatThrownBy(() -> service.reviewTechnical(
                INTERVIEW_ID, 1, QUESTION_ID, ANSWER_ID, TIMEOUT
        )).isInstanceOf(ModelException.class);

        InterviewNodeExecution failed = executions.values().iterator().next();
        assertThat(failed.status()).isEqualTo(InterviewNodeExecutionStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(1);

        TechnicalReview review = service.reviewTechnical(
                INTERVIEW_ID, 1, QUESTION_ID, ANSWER_ID, TIMEOUT
        );
        InterviewNodeExecution succeeded = executions.values().iterator().next();

        assertThat(review).isSameAs(technicalStored.get());
        assertThat(succeeded.status()).isEqualTo(InterviewNodeExecutionStatus.SUCCEEDED);
        assertThat(succeeded.attemptCount()).isEqualTo(2);
        verify(modelGateway, times(2)).generate(any(), any(), any());
    }

    private void configureRepositories(
            InterviewReviewRepository reviewRepository,
            InterviewNodeExecutionRepository executionRepository,
            Map<String, InterviewNodeExecution> executions,
            AtomicReference<TechnicalReview> technicalStored,
            AtomicReference<EvidenceReview> evidenceStored
    ) {
        when(reviewRepository.findTechnicalReviewByAnswer(OWNER, INTERVIEW_ID, ANSWER_ID))
                .thenAnswer(invocation -> Optional.ofNullable(technicalStored.get()));
        when(reviewRepository.findEvidenceReviewByAnswer(OWNER, INTERVIEW_ID, ANSWER_ID))
                .thenAnswer(invocation -> Optional.ofNullable(evidenceStored.get()));

        when(reviewRepository.claimTechnicalReview(any())).thenAnswer(invocation -> {
            TechnicalReview candidate = invocation.getArgument(0);
            technicalStored.compareAndSet(null, candidate);
            return technicalStored.get();
        });
        when(reviewRepository.claimEvidenceReview(any())).thenAnswer(invocation -> {
            EvidenceReview candidate = invocation.getArgument(0);
            evidenceStored.compareAndSet(null, candidate);
            return evidenceStored.get();
        });

        when(executionRepository.claim(any())).thenAnswer(invocation -> {
            InterviewNodeExecution candidate = invocation.getArgument(0);
            return executions.computeIfAbsent(
                    key(candidate.nodeName(), candidate.inputHash()),
                    ignored -> candidate
            );
        });
        when(executionRepository.updateIfVersionMatches(any(), any(), any(Long.class)))
                .thenAnswer(invocation -> {
                    InterviewNodeExecution updated = invocation.getArgument(1);
                    long expectedVersion = invocation.getArgument(2);
                    String key = key(updated.nodeName(), updated.inputHash());

                    synchronized (executions) {
                        InterviewNodeExecution current = executions.get(key);
                        if (current == null || current.version() != expectedVersion) return false;
                        executions.put(key, updated);
                        return true;
                    }
                });
    }

    private InterviewReviewPreparationService.PreparedReviews prepared(InterviewReviewPlan plan) {
        TechnicalReviewInput technicalInput = new TechnicalReviewInput(
                INTERVIEW_ID,
                1,
                QUESTION_ID,
                ANSWER_ID,
                "请说明虚拟线程的适用场景和边界。",
                "虚拟线程适合大量阻塞任务，不适合CPU密集计算。",
                List.of("Java并发"),
                List.of("TECHNICAL_CORRECTNESS", "DEPTH", "TRADE_OFFS", "COMMUNICATION"),
                List.of(
                        "TECHNICAL_CORRECTNESS评分规则",
                        "DEPTH评分规则",
                        "TRADE_OFFS评分规则",
                        "COMMUNICATION评分规则"
                )
        );
        EvidenceReviewInput evidenceInput = new EvidenceReviewInput(
                INTERVIEW_ID,
                1,
                QUESTION_ID,
                ANSWER_ID,
                technicalInput.question(),
                technicalInput.answer(),
                Map.of()
        );
        return new InterviewReviewPreparationService.PreparedReviews(
                OWNER,
                ROUND_ID,
                plan,
                technicalInput,
                evidenceInput
        );
    }

    private InterviewRoleModelGateway.Result<TechnicalReviewDraft> technicalResult() {
        TechnicalReviewDraft draft = new TechnicalReviewDraft(
                Map.of(
                        "TECHNICAL_CORRECTNESS", 4,
                        "DEPTH", 3,
                        "TRADE_OFFS", 4,
                        "COMMUNICATION", 4
                ),
                List.of("正确说明阻塞任务适用性"),
                List.of("未说明载体线程被固定的场景"),
                List.of("回答区分了阻塞任务和CPU密集任务"),
                "什么情况下虚拟线程会发生载体线程固定？"
        );
        return new InterviewRoleModelGateway.Result<>(
                draft,
                "technical-request",
                "stub-model",
                "technical-reviewer-v1",
                new ModelUsage(300, 120, 420),
                20,
                1,
                false,
                "a".repeat(64)
        );
    }

    private static String key(String nodeName, String inputHash) {
        return nodeName + ":" + inputHash;
    }
}