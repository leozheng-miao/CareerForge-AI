package com.leo.careerforgeai.interview.application.review;

import com.leo.careerforgeai.interview.application.model.review.EvidenceReviewInput;
import com.leo.careerforgeai.interview.application.model.review.TechnicalReviewInput;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.interview.domain.round.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.review.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.round.InterviewRound;
import com.leo.careerforgeai.interview.domain.round.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 重读当前回合和冻结证据并确定技术及证据评审输入
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Service
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        InterviewRoundRepository.class,
        MockInterviewInputSnapshotRepository.class,
        PersonalEvidenceArtifactRepository.class
})
public class InterviewReviewPreparationService {

    private static final int MAX_EVIDENCE_CHUNKS = 20;
    private static final int MAX_EVIDENCE_CHARS = 2_000;
    private static final List<String> SCORE_DIMENSIONS = List.of(
            "TECHNICAL_CORRECTNESS",
            "DEPTH",
            "TRADE_OFFS",
            "COMMUNICATION"
    );
    private static final List<String> SCORING_RUBRIC = List.of(
            "TECHNICAL_CORRECTNESS：0表示核心结论错误，5表示核心结论准确且无关键错误。",
            "DEPTH：0表示没有解释，5表示能够说明原理、机制和关键细节。",
            "TRADE_OFFS：0表示没有边界意识，5表示能够说明适用条件、限制和失败场景。",
            "COMMUNICATION：0表示无法理解，5表示结构清晰、重点明确且能够直接回答问题。"
    );

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewRoundRepository roundRepository;
    private final MockInterviewInputSnapshotRepository snapshotRepository;
    private final PersonalEvidenceArtifactRepository evidenceRepository;

    public InterviewReviewPreparationService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewRoundRepository roundRepository,
            MockInterviewInputSnapshotRepository snapshotRepository,
            PersonalEvidenceArtifactRepository evidenceRepository
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository不能为空");
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository不能为空");
    }

    @Transactional(readOnly = true)
    public PreparedReviews prepare(
            UUID interviewId,
            int roundNo,
            UUID questionId,
            UUID answerId
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(answerId, "answerId不能为空");
        if (roundNo < 1) throw new IllegalArgumentException("roundNo必须从1开始");

        ActorId ownerId = currentActor();
        MockInterviewSession session = sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
        if (session.status() != InterviewStatus.REVIEWING) {
            throw new IllegalStateException("只有REVIEWING状态可以准备评审");
        }

        InterviewRound round = roundRepository.findRoundByNumber(ownerId, interviewId, roundNo)
                .orElseThrow(() -> new IllegalStateException("当前评审回合不存在"));
        if (round.status() != InterviewRoundStatus.ANSWERED) {
            throw new IllegalStateException("只有ANSWERED回合可以准备评审");
        }

        InterviewQuestion question = roundRepository
                .findQuestionByRound(ownerId, interviewId, round.roundId())
                .orElseThrow(() -> new IllegalStateException("当前评审问题不存在"));
        InterviewAnswer answer = roundRepository
                .findAnswerByQuestion(ownerId, interviewId, question.questionId())
                .orElseThrow(() -> new IllegalStateException("当前评审答案不存在"));

        requireScope(ownerId, interviewId, questionId, answerId, round, question, answer);

        Map<String, String> evidenceByChunkId = evidenceForReview(session, question);
        InterviewReviewPlan plan = evidenceByChunkId.isEmpty()
                ? InterviewReviewPlan.TECHNICAL_ONLY
                : InterviewReviewPlan.TECHNICAL_AND_EVIDENCE;

        TechnicalReviewInput technicalInput = new TechnicalReviewInput(
                interviewId,
                roundNo,
                questionId,
                answerId,
                question.questionText(),
                answer.answerText(),
                question.targetSkills(),
                SCORE_DIMENSIONS,
                SCORING_RUBRIC
        );
        EvidenceReviewInput evidenceInput = new EvidenceReviewInput(
                interviewId,
                roundNo,
                questionId,
                answerId,
                question.questionText(),
                answer.answerText(),
                evidenceByChunkId
        );
        return new PreparedReviews(ownerId, round.roundId(), plan, technicalInput, evidenceInput);
    }

    private Map<String, String> evidenceForReview(
            MockInterviewSession session,
            InterviewQuestion question
    ) {
        if (question.questionType() != InterviewQuestionType.PROJECT_DEEP_DIVE) return Map.of();

        MockInterviewInputSnapshot snapshot = snapshotRepository
                .findById(session.ownerId(), session.inputSnapshotId())
                .orElseThrow(MockInterviewInputConflictException::new);
        if (!snapshot.ownerId().equals(session.ownerId())
                || !snapshot.snapshotHash().equals(session.inputSnapshotHash())) {
            throw new MockInterviewInputConflictException();
        }

        Map<String, String> available = new LinkedHashMap<>();
        for (MockInterviewInputSnapshot.ArtifactReference reference : snapshot.artifactReferences()) {
            PersonalEvidenceArtifact artifact = evidenceRepository.findVersionForSnapshot(
                    session.ownerId(),
                    reference.artifactId(),
                    reference.artifactVersion()
            ).orElseThrow(MockInterviewInputConflictException::new);

            if (!artifact.ownerId().equals(session.ownerId())
                    || !artifact.sourceHash().equals(reference.artifactSourceHash())
                    || artifact.status() == PersonalEvidenceStatus.REVOKED) {
                throw new MockInterviewInputConflictException();
            }

            for (PersonalEvidenceArtifact.Chunk chunk : artifact.chunks()) {
                int length = chunk.chunkContent().codePointCount(0, chunk.chunkContent().length());
                if (length > MAX_EVIDENCE_CHARS) {
                    throw new IllegalStateException("冻结证据片段超过证据评审输入边界");
                }
                available.putIfAbsent(chunk.evidenceChunkId(), chunk.chunkContent());
            }
        }

        Map<String, String> selected = new LinkedHashMap<>();
        if (!question.evidenceReferenceIds().isEmpty()) {
            for (String referenceId : question.evidenceReferenceIds()) {
                String content = available.get(referenceId);
                if (content == null) throw new MockInterviewInputConflictException();
                selected.put(referenceId, content);
            }
        } else {
            for (Map.Entry<String, String> entry : available.entrySet()) {
                selected.put(entry.getKey(), entry.getValue());
                if (selected.size() == MAX_EVIDENCE_CHUNKS) break;
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(selected));
    }

    private void requireScope(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId,
            UUID answerId,
            InterviewRound round,
            InterviewQuestion question,
            InterviewAnswer answer
    ) {
        if (!round.ownerId().equals(ownerId)
                || !round.interviewId().equals(interviewId)
                || !question.ownerId().equals(ownerId)
                || !question.interviewId().equals(interviewId)
                || !question.roundId().equals(round.roundId())
                || !question.questionId().equals(questionId)
                || !answer.ownerId().equals(ownerId)
                || !answer.interviewId().equals(interviewId)
                || !answer.roundId().equals(round.roundId())
                || !answer.questionId().equals(questionId)
                || !answer.answerId().equals(answerId)) {
            throw new IllegalStateException("评审Session、回合、问题、答案或owner作用域不一致");
        }
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存一次评审准备得到的可信作用域、适用性和两个角色输入
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param ownerId 当前面试owner
     * @param roundId 当前回合UUID
     * @param plan 本轮评审适用性
     * @param technicalInput 技术评审输入
     * @param evidenceInput 证据评审输入，不适用时evidenceByChunkId为空
     **/
    public record PreparedReviews(
            ActorId ownerId,
            UUID roundId,
            InterviewReviewPlan plan,
            TechnicalReviewInput technicalInput,
            EvidenceReviewInput evidenceInput
    ) {
        public PreparedReviews {
            Objects.requireNonNull(ownerId, "ownerId不能为空");
            Objects.requireNonNull(roundId, "roundId不能为空");
            Objects.requireNonNull(plan, "plan不能为空");
            Objects.requireNonNull(technicalInput, "technicalInput不能为空");
            Objects.requireNonNull(evidenceInput, "evidenceInput不能为空");
        }
    }
}