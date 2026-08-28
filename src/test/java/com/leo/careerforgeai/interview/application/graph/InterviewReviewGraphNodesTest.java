package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.model.contract.EvidenceReviewInput;
import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewInput;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.review.InterviewReviewApplicationService;
import com.leo.careerforgeai.interview.application.review.InterviewReviewPreparationService;
import com.leo.careerforgeai.interview.domain.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.EvidenceReview;
import com.leo.careerforgeai.interview.domain.EvidenceReviewSource;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.TechnicalReview;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证评审准备、两个独立分支和汇合节点只传递ID并重读MySQL事实
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewReviewGraphNodesTest {

    @Test
    void shouldPrepareRunIndependentBranchesAndJoinPersistedReviews() {
        ActorId owner = new ActorId("review-graph-owner");
        UUID interviewId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID roundId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID questionId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID answerId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID technicalReviewId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        UUID evidenceReviewId = UUID.fromString("00000000-0000-0000-0000-000000000006");
        String evidenceChunkId = "b".repeat(64);

        InterviewReviewPreparationService preparationService =
                mock(InterviewReviewPreparationService.class);
        InterviewReviewApplicationService reviewService =
                mock(InterviewReviewApplicationService.class);
        InterviewReviewRepository reviewRepository =
                mock(InterviewReviewRepository.class);
        TechnicalReview technicalReview = mock(TechnicalReview.class);
        EvidenceReview evidenceReview = mock(EvidenceReview.class);

        TechnicalReviewInput technicalInput = new TechnicalReviewInput(
                interviewId,
                1,
                questionId,
                answerId,
                "请说明项目中的并发控制方案。",
                "我使用CAS和数据库唯一约束处理并发竞争。",
                List.of("Java并发"),
                List.of("TECHNICAL_CORRECTNESS"),
                List.of("TECHNICAL_CORRECTNESS评分规则")
        );
        EvidenceReviewInput evidenceInput = new EvidenceReviewInput(
                interviewId,
                1,
                questionId,
                answerId,
                technicalInput.question(),
                technicalInput.answer(),
                Map.of(
                        evidenceChunkId,
                        "项目使用CAS和数据库唯一约束保证状态幂等。"
                )
        );
        var prepared = new InterviewReviewPreparationService.PreparedReviews(
                owner,
                roundId,
                InterviewReviewPlan.TECHNICAL_AND_EVIDENCE,
                technicalInput,
                evidenceInput
        );

        when(preparationService.prepare(interviewId, 1, questionId, answerId))
                .thenReturn(prepared);
        when(reviewService.reviewTechnical(
                interviewId,
                1,
                questionId,
                answerId,
                Duration.ofSeconds(30)
        )).thenReturn(technicalReview);
        when(reviewService.reviewEvidence(
                interviewId,
                1,
                questionId,
                answerId,
                Duration.ofSeconds(30)
        )).thenReturn(evidenceReview);

        when(technicalReview.technicalReviewId()).thenReturn(technicalReviewId);
        when(technicalReview.ownerId()).thenReturn(owner);
        when(technicalReview.interviewId()).thenReturn(interviewId);
        when(technicalReview.questionId()).thenReturn(questionId);
        when(technicalReview.answerId()).thenReturn(answerId);

        when(evidenceReview.evidenceReviewId()).thenReturn(evidenceReviewId);
        when(evidenceReview.ownerId()).thenReturn(owner);
        when(evidenceReview.interviewId()).thenReturn(interviewId);
        when(evidenceReview.questionId()).thenReturn(questionId);
        when(evidenceReview.answerId()).thenReturn(answerId);
        when(evidenceReview.source()).thenReturn(EvidenceReviewSource.MODEL);
        when(evidenceReview.verdict())
                .thenReturn(EvidenceConsistencyVerdict.SUPPORTED);

        when(reviewRepository.findTechnicalReviewById(
                owner,
                interviewId,
                technicalReviewId
        )).thenReturn(Optional.of(technicalReview));
        when(reviewRepository.findEvidenceReviewById(
                owner,
                interviewId,
                evidenceReviewId
        )).thenReturn(Optional.of(evidenceReview));

        InterviewReviewGraphNodes nodes = new InterviewReviewGraphNodes(
                () -> owner,
                preparationService,
                reviewService,
                reviewRepository,
                Duration.ofSeconds(30)
        );

        InterviewGraphState initial = state(
                interviewId,
                questionId,
                answerId,
                Map.of()
        );
        Map<String, Object> prepareUpdate = nodes.prepareReviews(initial);

        InterviewGraphState preparedState = state(
                interviewId,
                questionId,
                answerId,
                prepareUpdate
        );
        Map<String, Object> technicalUpdate =
                nodes.technicalReview(preparedState);
        Map<String, Object> evidenceUpdate =
                nodes.evidenceReview(preparedState);

        Map<String, Object> joinedUpdates = new HashMap<>();
        joinedUpdates.putAll(prepareUpdate);
        joinedUpdates.putAll(technicalUpdate);
        joinedUpdates.putAll(evidenceUpdate);

        InterviewGraphState joinedState = state(
                interviewId,
                questionId,
                answerId,
                joinedUpdates
        );

        assertThat(nodes.joinReviews(joinedState)).isEmpty();
        assertThat(prepareUpdate).containsEntry(
                InterviewGraphState.REVIEW_PLAN,
                InterviewReviewPlan.TECHNICAL_AND_EVIDENCE.name()
        );
        assertThat(technicalUpdate).containsEntry(
                InterviewGraphState.TECHNICAL_REVIEW_ID,
                technicalReviewId.toString()
        );
        assertThat(evidenceUpdate).containsEntry(
                InterviewGraphState.EVIDENCE_REVIEW_ID,
                evidenceReviewId.toString()
        );
        assertThat(technicalUpdate)
                .doesNotContainKey(InterviewGraphState.EVIDENCE_REVIEW_ID);
        assertThat(evidenceUpdate)
                .doesNotContainKey(InterviewGraphState.TECHNICAL_REVIEW_ID);

        verify(reviewRepository).findTechnicalReviewById(
                owner,
                interviewId,
                technicalReviewId
        );
        verify(reviewRepository).findEvidenceReviewById(
                owner,
                interviewId,
                evidenceReviewId
        );
    }

    private InterviewGraphState state(
            UUID interviewId,
            UUID questionId,
            UUID answerId,
            Map<String, Object> additional
    ) {
        Map<String, Object> data = new HashMap<>(
                InterviewGraphState.initialData(
                        interviewId,
                        InterviewMode.TARGETED_MOCK,
                        "a".repeat(64)
                )
        );
        data.put(
                InterviewGraphState.CURRENT_ROUND,
                1
        );
        data.put(
                InterviewGraphState.CURRENT_QUESTION_ID,
                questionId.toString()
        );
        data.put(
                InterviewGraphState.ANSWER_ID,
                answerId.toString()
        );
        data.putAll(additional);
        return new InterviewGraphState(data);
    }
}