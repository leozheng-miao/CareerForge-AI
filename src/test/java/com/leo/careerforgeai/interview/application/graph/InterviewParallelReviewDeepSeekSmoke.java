package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.CareerForgeAiApplication;
import com.leo.careerforgeai.interview.application.model.contract.EvidenceReviewDraft;
import com.leo.careerforgeai.interview.application.model.contract.EvidenceReviewInput;
import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewDraft;
import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewInput;
import com.leo.careerforgeai.interview.application.model.validation.EvidenceReviewRoleContract;
import com.leo.careerforgeai.interview.application.model.validation.TechnicalReviewRoleContract;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 使用真实DeepSeek验证LangGraph4j双评审Fork-Join、并发重叠和模型Bulkhead边界
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@SpringBootTest(
        classes = CareerForgeAiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "careerforge.persistence.enabled=false",
                "spring.flyway.enabled=false",
                "spring.ai.chat.client.enabled=false",
                "spring.ai.model.chat=none",
                "careerforge.model-call-bulkhead.max-concurrent-calls=2"
        }
)
@EnabledIfSystemProperty(named = "cp7.deepseek.smoke", matches = "true")
class InterviewParallelReviewDeepSeekSmoke {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(45);
    private static final UUID INTERVIEW_ID = id("cp7-parallel-interview");
    private static final UUID QUESTION_ID = id("cp7-parallel-question");
    private static final UUID ANSWER_ID = id("cp7-parallel-answer");
    private static final UUID TECHNICAL_REVIEW_ID = id("cp7-parallel-technical-review");
    private static final UUID EVIDENCE_REVIEW_ID = id("cp7-parallel-evidence-review");
    private static final String QUESTION = "你在项目中如何限制同一用户的并发任务，并保证异常后许可能够释放？";
    private static final String ANSWER = "项目使用owner级Semaphore限制每个用户最多同时执行两个任务，并在finally中释放许可。测试覆盖成功、异常和取消路径。";
    private static final String EVIDENCE_CHUNK_ID = "b".repeat(64);

    @Autowired
    private InterviewRoleModelGateway modelGateway;

    @Autowired
    private TechnicalReviewRoleContract technicalContract;

    @Autowired
    private EvidenceReviewRoleContract evidenceContract;

    @Test
    void shouldRunRealTechnicalAndEvidenceReviewsInParallelWithinBulkhead() throws Exception {
        InterviewGraphNodes nodes = mock(InterviewGraphNodes.class);
        InterviewReviewGraphNodes reviewNodes = mock(InterviewReviewGraphNodes.class);
        TechnicalReviewInput technicalInput = technicalInput();
        EvidenceReviewInput evidenceInput = evidenceInput();
        CountDownLatch bothBranchesReady = new CountDownLatch(2);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        AtomicLong technicalStarted = new AtomicLong();
        AtomicLong technicalFinished = new AtomicLong();
        AtomicLong evidenceStarted = new AtomicLong();
        AtomicLong evidenceFinished = new AtomicLong();
        AtomicReference<InterviewRoleModelGateway.Result<TechnicalReviewDraft>> technicalResult =
                new AtomicReference<>();
        AtomicReference<InterviewRoleModelGateway.Result<EvidenceReviewDraft>> evidenceResult =
                new AtomicReference<>();

        when(nodes.loadFrozenContext(any(InterviewGraphState.class))).thenReturn(Map.of());
        when(nodes.generateAndPersistQuestion(any(InterviewGraphState.class)))
                .thenReturn(InterviewGraphState.waitingForAnswerUpdate(1, QUESTION_ID));
        when(nodes.validateAnswerResume(any(InterviewGraphState.class)))
                .thenReturn(InterviewGraphState.clearWaitReasonUpdate());
        when(reviewNodes.prepareReviews(any(InterviewGraphState.class)))
                .thenReturn(Map.of(
                        InterviewGraphState.REVIEW_PLAN,
                        InterviewReviewPlan.TECHNICAL_AND_EVIDENCE.name()
                ));
        when(reviewNodes.technicalReview(any(InterviewGraphState.class))).thenAnswer(invocation -> {
            awaitBothBranches(bothBranchesReady);
            technicalStarted.set(System.nanoTime());
            observeEnter(inFlight, maxInFlight);
            try {
                technicalResult.set(modelGateway.generate(technicalContract, technicalInput, CALL_TIMEOUT));
                return Map.of(InterviewGraphState.TECHNICAL_REVIEW_ID, TECHNICAL_REVIEW_ID.toString());
            } finally {
                technicalFinished.set(System.nanoTime());
                inFlight.decrementAndGet();
            }
        });
        when(reviewNodes.evidenceReview(any(InterviewGraphState.class))).thenAnswer(invocation -> {
            awaitBothBranches(bothBranchesReady);
            evidenceStarted.set(System.nanoTime());
            observeEnter(inFlight, maxInFlight);
            try {
                evidenceResult.set(modelGateway.generate(evidenceContract, evidenceInput, CALL_TIMEOUT));
                return Map.of(InterviewGraphState.EVIDENCE_REVIEW_ID, EVIDENCE_REVIEW_ID.toString());
            } finally {
                evidenceFinished.set(System.nanoTime());
                inFlight.decrementAndGet();
            }
        });
        when(reviewNodes.joinReviews(any(InterviewGraphState.class))).thenReturn(Map.of());

        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("cp7-real-review-", 0).factory()
        )) {
            var graph = new InterviewGraphWorkflow(nodes, reviewNodes).compile(new MemorySaver());
            RunnableConfig config = RunnableConfig.builder()
                    .threadId("cp7-real-" + INTERVIEW_ID)
                    .addParallelNodeExecutor(InterviewGraphWorkflow.PREPARE_REVIEWS, executor)
                    .build();

            graph.invokeFinal(
                    GraphInput.args(InterviewGraphState.initialData(
                            INTERVIEW_ID, InterviewMode.TARGETED_MOCK, "a".repeat(64)
                    )),
                    config
            ).orElseThrow();

            long graphResumeStarted = System.nanoTime();
            InterviewGraphState completed = graph.invoke(
                    GraphInput.resume(InterviewGraphState.answerResumeUpdate(ANSWER_ID)),
                    config
            ).orElseThrow();
            long graphResumeDurationMs = elapsedMillis(graphResumeStarted, System.nanoTime());

            long technicalDurationMs = elapsedMillis(technicalStarted.get(), technicalFinished.get());
            long evidenceDurationMs = elapsedMillis(evidenceStarted.get(), evidenceFinished.get());
            long serialEstimateMs = technicalDurationMs + evidenceDurationMs;
            long parallelReviewSpanMs = elapsedMillis(
                    Math.min(technicalStarted.get(), evidenceStarted.get()),
                    Math.max(technicalFinished.get(), evidenceFinished.get())
            );
            long overlapMs = elapsedMillis(
                    Math.max(technicalStarted.get(), evidenceStarted.get()),
                    Math.min(technicalFinished.get(), evidenceFinished.get())
            );

            assertThat(technicalResult.get()).isNotNull();
            assertThat(evidenceResult.get()).isNotNull();
            assertThat(completed.data())
                    .containsEntry(InterviewGraphState.TECHNICAL_REVIEW_ID, TECHNICAL_REVIEW_ID.toString())
                    .containsEntry(InterviewGraphState.EVIDENCE_REVIEW_ID, EVIDENCE_REVIEW_ID.toString());
            assertThat(maxInFlight.get()).isEqualTo(2);
            assertThat(overlapMs).isPositive();
            assertThat(parallelReviewSpanMs).isLessThan(serialEstimateMs);

            System.out.printf(
                    Locale.ROOT,
                    "caseId=CP7-PARALLEL-REAL, status=SUCCEEDED, technicalDurationMs=%d, evidenceDurationMs=%d, serialEstimateMs=%d, parallelReviewSpanMs=%d, graphResumeDurationMs=%d, savedMs=%d, overlapMs=%d, maxInFlight=%d, bulkheadLimit=2, technicalTokens=%d, evidenceTokens=%d%n",
                    technicalDurationMs,
                    evidenceDurationMs,
                    serialEstimateMs,
                    parallelReviewSpanMs,
                    graphResumeDurationMs,
                    serialEstimateMs - parallelReviewSpanMs,
                    overlapMs,
                    maxInFlight.get(),
                    technicalResult.get().usage().totalTokens(),
                    evidenceResult.get().usage().totalTokens()
            );
        }
    }

    private TechnicalReviewInput technicalInput() {
        return new TechnicalReviewInput(
                INTERVIEW_ID,
                1,
                QUESTION_ID,
                ANSWER_ID,
                QUESTION,
                ANSWER,
                List.of("理解并发准入和异常资源释放", "能够说明owner级隔离"),
                List.of("CORRECTNESS", "DEPTH", "FAILURE_HANDLING"),
                List.of(
                        "CORRECTNESS：说明Semaphore限制并发。",
                        "DEPTH：说明owner级隔离的作用。",
                        "FAILURE_HANDLING：说明成功、异常和取消路径的许可释放。"
                )
        );
    }

    private EvidenceReviewInput evidenceInput() {
        return new EvidenceReviewInput(
                INTERVIEW_ID,
                1,
                QUESTION_ID,
                ANSWER_ID,
                QUESTION,
                ANSWER,
                Map.of(
                        EVIDENCE_CHUNK_ID,
                        "项目记录：owner级Semaphore最大并发数为2，并验证成功、异常和取消路径最终释放许可。"
                )
        );
    }

    private void awaitBothBranches(CountDownLatch bothBranchesReady) throws InterruptedException {
        bothBranchesReady.countDown();
        if (!bothBranchesReady.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("两个评审分支没有并行进入");
        }
    }

    private void observeEnter(AtomicInteger inFlight, AtomicInteger maxInFlight) {
        int current = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(current, Math::max);
    }

    private long elapsedMillis(long startedNanos, long finishedNanos) {
        return Math.max(0, Duration.ofNanos(finishedNanos - startedNanos).toMillis());
    }

    private static UUID id(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}