package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.InterviewWaitReason;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证面试Graph首题中断、答案恢复和双评审真实fork-join
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewGraphWorkflowTest {

    @Test
    void shouldInterruptAfterPersistedQuestionUsingMinimalCheckpointState() throws Exception {
        UUID interviewId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID questionId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InterviewGraphNodes nodes = mock(InterviewGraphNodes.class);
        InterviewReviewGraphNodes reviewNodes = mock(InterviewReviewGraphNodes.class);

        when(nodes.loadFrozenContext(any(InterviewGraphState.class))).thenReturn(Map.of());
        when(nodes.generateAndPersistQuestion(any(InterviewGraphState.class)))
                .thenReturn(InterviewGraphState.waitingForAnswerUpdate(1, questionId));

        var workflow = new InterviewGraphWorkflow(nodes, reviewNodes).compile(new MemorySaver());
        RunnableConfig config = RunnableConfig.builder().threadId("cp7-interrupt-" + interviewId).build();

        var interrupted = workflow.invokeFinal(
                GraphInput.args(InterviewGraphState.initialData(
                        interviewId, InterviewMode.TARGETED_MOCK, "a".repeat(64)
                )),
                config
        ).orElseThrow();

        var checkpoint = workflow.lastStateOf(config).orElseThrow();
        InterviewGraphState state = checkpoint.state();

        assertThat(interrupted.node()).isEqualTo(InterviewGraphWorkflow.GENERATE_AND_PERSIST_QUESTION);
        assertThat(checkpoint.next()).isEqualTo(InterviewGraphWorkflow.VALIDATE_ANSWER_RESUME);
        assertThat(state.interviewId()).isEqualTo(interviewId);
        assertThat(state.currentRound()).isEqualTo(1);
        assertThat(state.currentQuestionId()).contains(questionId);
        assertThat(state.waitReason()).contains(InterviewWaitReason.WAITING_FOR_ANSWER);
        assertThat(state.data()).doesNotContainKeys("question", "questionDraft", "resumeContent", "targetRole");

        verify(nodes).loadFrozenContext(any(InterviewGraphState.class));
        verify(nodes).generateAndPersistQuestion(any(InterviewGraphState.class));
    }

    @Test
    void shouldResumeAndRunTechnicalAndEvidenceReviewsInParallel() throws Exception {
        UUID interviewId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID questionId = UUID.fromString("00000000-0000-0000-0000-000000000012");
        UUID answerId = UUID.fromString("00000000-0000-0000-0000-000000000013");
        UUID technicalReviewId = UUID.fromString("00000000-0000-0000-0000-000000000014");
        UUID evidenceReviewId = UUID.fromString("00000000-0000-0000-0000-000000000015");
        InterviewGraphNodes nodes = mock(InterviewGraphNodes.class);
        InterviewReviewGraphNodes reviewNodes = mock(InterviewReviewGraphNodes.class);
        CyclicBarrier branchBarrier = new CyclicBarrier(2);
        AtomicReference<String> technicalThread = new AtomicReference<>();
        AtomicReference<String> evidenceThread = new AtomicReference<>();

        when(nodes.loadFrozenContext(any(InterviewGraphState.class))).thenReturn(Map.of());
        when(nodes.generateAndPersistQuestion(any(InterviewGraphState.class)))
                .thenReturn(InterviewGraphState.waitingForAnswerUpdate(1, questionId));
        when(nodes.validateAnswerResume(any(InterviewGraphState.class)))
                .thenReturn(InterviewGraphState.clearWaitReasonUpdate());
        when(reviewNodes.prepareReviews(any(InterviewGraphState.class)))
                .thenReturn(Map.of(InterviewGraphState.REVIEW_PLAN, InterviewReviewPlan.TECHNICAL_AND_EVIDENCE.name()));
        when(reviewNodes.technicalReview(any(InterviewGraphState.class))).thenAnswer(invocation -> {
            technicalThread.set(Thread.currentThread().getName());
            branchBarrier.await(2, TimeUnit.SECONDS);
            return Map.of(InterviewGraphState.TECHNICAL_REVIEW_ID, technicalReviewId.toString());
        });
        when(reviewNodes.evidenceReview(any(InterviewGraphState.class))).thenAnswer(invocation -> {
            evidenceThread.set(Thread.currentThread().getName());
            branchBarrier.await(2, TimeUnit.SECONDS);
            return Map.of(InterviewGraphState.EVIDENCE_REVIEW_ID, evidenceReviewId.toString());
        });
        when(reviewNodes.joinReviews(any(InterviewGraphState.class))).thenReturn(Map.of());

        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("cp7-review-", 0).factory()
        )) {
            var workflow = new InterviewGraphWorkflow(nodes, reviewNodes).compile(new MemorySaver());
            RunnableConfig config = RunnableConfig.builder()
                    .threadId("cp7-resume-" + interviewId)
                    .addParallelNodeExecutor(InterviewGraphWorkflow.PREPARE_REVIEWS, executor)
                    .build();

            workflow.invokeFinal(
                    GraphInput.args(InterviewGraphState.initialData(
                            interviewId, InterviewMode.TARGETED_MOCK, "a".repeat(64)
                    )),
                    config
            ).orElseThrow();

            InterviewGraphState completed = workflow.invoke(
                    GraphInput.resume(InterviewGraphState.answerResumeUpdate(answerId)),
                    config
            ).orElseThrow();

            assertThat(completed.answerId()).contains(answerId);
            assertThat(completed.waitReason()).isEmpty();
            assertThat(completed.data())
                    .containsEntry(InterviewGraphState.REVIEW_PLAN, InterviewReviewPlan.TECHNICAL_AND_EVIDENCE.name())
                    .containsEntry(InterviewGraphState.TECHNICAL_REVIEW_ID, technicalReviewId.toString())
                    .containsEntry(InterviewGraphState.EVIDENCE_REVIEW_ID, evidenceReviewId.toString());
            assertThat(technicalThread.get()).startsWith("cp7-review-");
            assertThat(evidenceThread.get()).startsWith("cp7-review-");
            assertThat(technicalThread.get()).isNotEqualTo(evidenceThread.get());

            verify(nodes).validateAnswerResume(any(InterviewGraphState.class));
            verify(reviewNodes).prepareReviews(any(InterviewGraphState.class));
            verify(reviewNodes).technicalReview(any(InterviewGraphState.class));
            verify(reviewNodes).evidenceReview(any(InterviewGraphState.class));
            verify(reviewNodes).joinReviews(any(InterviewGraphState.class));
        }
    }
}