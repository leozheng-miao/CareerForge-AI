package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.model.contract.EvidenceReviewInput;
import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewInput;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.review.InterviewReviewApplicationService;
import com.leo.careerforgeai.interview.application.review.InterviewReviewPreparationService;
import com.leo.careerforgeai.interview.domain.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.EvidenceReview;
import com.leo.careerforgeai.interview.domain.EvidenceReviewSource;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.TechnicalReview;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 实现评审准备、技术评审、证据评审和fork-join汇合校验节点
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
@ConditionalOnBean({
        InterviewReviewPreparationService.class,
        InterviewReviewApplicationService.class,
        InterviewReviewRepository.class
})
public class InterviewReviewGraphNodes {

    private final CurrentActorProvider currentActorProvider;
    private final InterviewReviewPreparationService preparationService;
    private final InterviewReviewApplicationService reviewService;
    private final InterviewReviewRepository reviewRepository;
    private final Duration modelCallTimeout;

    public InterviewReviewGraphNodes(
            CurrentActorProvider currentActorProvider,
            InterviewReviewPreparationService preparationService,
            InterviewReviewApplicationService reviewService,
            InterviewReviewRepository reviewRepository,
            @Value("${careerforge.agent.loop.model-call-timeout}") Duration modelCallTimeout
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.preparationService = Objects.requireNonNull(preparationService, "preparationService不能为空");
        this.reviewService = Objects.requireNonNull(reviewService, "reviewService不能为空");
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository不能为空");
        if (modelCallTimeout == null || modelCallTimeout.isZero() || modelCallTimeout.isNegative()) {
            throw new IllegalArgumentException("modelCallTimeout必须大于0");
        }
        this.modelCallTimeout = modelCallTimeout;
    }

    public Map<String, Object> prepareReviews(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        UUID questionId = state.currentQuestionId()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少currentQuestionId"));
        UUID answerId = state.answerId()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少answerId"));

        InterviewReviewPreparationService.PreparedReviews prepared =
                preparationService.prepare(
                        state.interviewId(),
                        requireRound(state),
                        questionId,
                        answerId
                );

        requirePreparedScope(state, questionId, answerId, prepared);
        return Map.of(
                InterviewGraphState.REVIEW_PLAN,
                prepared.plan().name()
        );
    }

    public Map<String, Object> technicalReview(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        UUID questionId = requiredQuestionId(state);
        UUID answerId = requiredAnswerId(state);

        TechnicalReview review = reviewService.reviewTechnical(
                state.interviewId(),
                requireRound(state),
                questionId,
                answerId,
                modelCallTimeout
        );
        requireTechnicalScope(state, questionId, answerId, review);

        return Map.of(
                InterviewGraphState.TECHNICAL_REVIEW_ID,
                review.technicalReviewId().toString()
        );
    }

    public Map<String, Object> evidenceReview(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        UUID questionId = requiredQuestionId(state);
        UUID answerId = requiredAnswerId(state);

        EvidenceReview review = reviewService.reviewEvidence(
                state.interviewId(),
                requireRound(state),
                questionId,
                answerId,
                modelCallTimeout
        );
        requireEvidenceScope(state, questionId, answerId, review);

        return Map.of(
                InterviewGraphState.EVIDENCE_REVIEW_ID,
                review.evidenceReviewId().toString()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> joinReviews(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        ActorId ownerId = currentActor();
        UUID questionId = requiredQuestionId(state);
        UUID answerId = requiredAnswerId(state);
        UUID technicalReviewId = requiredStateUuid(
                state,
                InterviewGraphState.TECHNICAL_REVIEW_ID
        );
        UUID evidenceReviewId = requiredStateUuid(
                state,
                InterviewGraphState.EVIDENCE_REVIEW_ID
        );
        InterviewReviewPlan plan = requiredReviewPlan(state);

        TechnicalReview technicalReview = reviewRepository
                .findTechnicalReviewById(
                        ownerId,
                        state.interviewId(),
                        technicalReviewId
                )
                .orElseThrow(() -> new IllegalStateException("MySQL缺少技术评审事实"));
        EvidenceReview evidenceReview = reviewRepository
                .findEvidenceReviewById(
                        ownerId,
                        state.interviewId(),
                        evidenceReviewId
                )
                .orElseThrow(() -> new IllegalStateException("MySQL缺少证据评审事实"));

        requireTechnicalScope(state, questionId, answerId, technicalReview);
        requireEvidenceScope(state, questionId, answerId, evidenceReview);
        requireEvidencePlan(plan, evidenceReview);
        return Map.of();
    }

    private void requirePreparedScope(
            InterviewGraphState state,
            UUID questionId,
            UUID answerId,
            InterviewReviewPreparationService.PreparedReviews prepared
    ) {
        ActorId ownerId = currentActor();
        TechnicalReviewInput technicalInput = prepared.technicalInput();
        EvidenceReviewInput evidenceInput = prepared.evidenceInput();

        if (!prepared.ownerId().equals(ownerId)
                || !technicalInput.interviewId().equals(state.interviewId())
                || technicalInput.roundNo() != state.currentRound()
                || !technicalInput.questionId().equals(questionId)
                || !technicalInput.answerId().equals(answerId)
                || !evidenceInput.interviewId().equals(state.interviewId())
                || evidenceInput.roundNo() != state.currentRound()
                || !evidenceInput.questionId().equals(questionId)
                || !evidenceInput.answerId().equals(answerId)) {
            throw new IllegalStateException("评审准备结果与Graph State作用域不一致");
        }

        boolean evidenceApplicable = !evidenceInput.evidenceByChunkId().isEmpty();
        if (evidenceApplicable
                != (prepared.plan() == InterviewReviewPlan.TECHNICAL_AND_EVIDENCE)) {
            throw new IllegalStateException("评审计划与证据适用性不一致");
        }
    }

    private void requireTechnicalScope(
            InterviewGraphState state,
            UUID questionId,
            UUID answerId,
            TechnicalReview review
    ) {
        ActorId ownerId = currentActor();
        if (!review.ownerId().equals(ownerId)
                || !review.interviewId().equals(state.interviewId())
                || !review.questionId().equals(questionId)
                || !review.answerId().equals(answerId)) {
            throw new IllegalStateException("技术评审与Graph State作用域不一致");
        }
    }

    private void requireEvidenceScope(
            InterviewGraphState state,
            UUID questionId,
            UUID answerId,
            EvidenceReview review
    ) {
        ActorId ownerId = currentActor();
        if (!review.ownerId().equals(ownerId)
                || !review.interviewId().equals(state.interviewId())
                || !review.questionId().equals(questionId)
                || !review.answerId().equals(answerId)) {
            throw new IllegalStateException("证据评审与Graph State作用域不一致");
        }
    }

    private void requireEvidencePlan(
            InterviewReviewPlan plan,
            EvidenceReview review
    ) {
        if (plan == InterviewReviewPlan.TECHNICAL_ONLY) {
            if (review.source() != EvidenceReviewSource.JAVA
                    || review.verdict() != EvidenceConsistencyVerdict.NOT_APPLICABLE) {
                throw new IllegalStateException("TECHNICAL_ONLY必须对应Java NOT_APPLICABLE证据评审");
            }
            return;
        }

        if (review.source() != EvidenceReviewSource.MODEL
                || review.verdict() == EvidenceConsistencyVerdict.NOT_APPLICABLE) {
            throw new IllegalStateException("TECHNICAL_AND_EVIDENCE必须对应有效模型证据评审");
        }
    }

    private int requireRound(InterviewGraphState state) {
        if (state.currentRound() < 1) {
            throw new IllegalStateException("Checkpoint尚未进入有效回合");
        }
        return state.currentRound();
    }

    private UUID requiredQuestionId(InterviewGraphState state) {
        return state.currentQuestionId()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少currentQuestionId"));
    }

    private UUID requiredAnswerId(InterviewGraphState state) {
        return state.answerId()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少answerId"));
    }

    private UUID requiredStateUuid(
            InterviewGraphState state,
            String key
    ) {
        Object value = state.data().get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalStateException("Checkpoint缺少" + key);
        }
        try {
            return UUID.fromString(stringValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Checkpoint中的" + key + "不是合法UUID", exception);
        }
    }

    private InterviewReviewPlan requiredReviewPlan(InterviewGraphState state) {
        Object value = state.data().get(InterviewGraphState.REVIEW_PLAN);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalStateException("Checkpoint缺少reviewPlan");
        }
        try {
            return InterviewReviewPlan.valueOf(stringValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Checkpoint中的reviewPlan不合法", exception);
        }
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(
                currentActorProvider.currentActor(),
                "currentActor不能为空"
        );
    }
}