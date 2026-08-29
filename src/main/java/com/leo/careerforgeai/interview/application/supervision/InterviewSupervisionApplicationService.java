package com.leo.careerforgeai.interview.application.supervision;

import com.leo.careerforgeai.interview.application.port.InterviewNodeExecutionRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.domain.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.EvidenceReview;
import com.leo.careerforgeai.interview.domain.EvidenceReviewSource;
import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.interview.domain.TechnicalReview;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 从MySQL重读当前回合、评审和预算事实并调用Java Supervisor生成只读路由决策
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Service
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        InterviewRoundRepository.class,
        InterviewReviewRepository.class,
        InterviewNodeExecutionRepository.class,
        InterviewSupervisor.class
})
public class InterviewSupervisionApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewRoundRepository roundRepository;
    private final InterviewReviewRepository reviewRepository;
    private final InterviewNodeExecutionRepository executionRepository;
    private final InterviewSupervisor supervisor;
    private final InterviewBudgetForecast forecast;

    public InterviewSupervisionApplicationService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewRoundRepository roundRepository,
            InterviewReviewRepository reviewRepository,
            InterviewNodeExecutionRepository executionRepository,
            InterviewSupervisor supervisor,
            @Value("${careerforge.interview.supervisor.next-round-model-calls:3}") int nextRoundModelCalls,
            @Value("${careerforge.interview.supervisor.next-round-tokens:4000}") long nextRoundTokens,
            @Value("${careerforge.interview.supervisor.report-model-calls:1}") int reportModelCalls,
            @Value("${careerforge.interview.supervisor.report-tokens:4000}") long reportTokens
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository不能为空");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository不能为空");
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor不能为空");
        this.forecast = new InterviewBudgetForecast(nextRoundModelCalls, nextRoundTokens, reportModelCalls, reportTokens);
    }

    @Transactional(readOnly = true)
    public InterviewSupervisorDecision superviseRound(
            UUID interviewId,
            int roundNo,
            UUID questionId,
            UUID answerId,
            UUID technicalReviewId,
            UUID evidenceReviewId
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(answerId, "answerId不能为空");
        Objects.requireNonNull(technicalReviewId, "technicalReviewId不能为空");
        Objects.requireNonNull(evidenceReviewId, "evidenceReviewId不能为空");
        if (roundNo < 1) throw new IllegalArgumentException("roundNo必须从1开始");

        ActorId ownerId = currentActor();
        MockInterviewSession session = sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
        InterviewRound round = roundRepository.findRoundByNumber(ownerId, interviewId, roundNo)
                .orElseThrow(() -> new IllegalStateException("MySQL缺少当前面试回合"));
        InterviewQuestion question = roundRepository.findQuestionByRound(ownerId, interviewId, round.roundId())
                .orElseThrow(() -> new IllegalStateException("MySQL缺少当前问题"));
        InterviewAnswer answer = roundRepository.findAnswerByQuestion(ownerId, interviewId, questionId)
                .orElseThrow(() -> new IllegalStateException("MySQL缺少当前问题答案"));
        TechnicalReview technicalReview = reviewRepository.findTechnicalReviewById(ownerId, interviewId, technicalReviewId)
                .orElseThrow(() -> new IllegalStateException("MySQL缺少技术评审事实"));
        EvidenceReview evidenceReview = reviewRepository.findEvidenceReviewById(ownerId, interviewId, evidenceReviewId)
                .orElseThrow(() -> new IllegalStateException("MySQL缺少证据评审事实"));

        requireScope(ownerId, interviewId, roundNo, questionId, answerId, session, round, question, answer, technicalReview, evidenceReview);
        InterviewReviewPlan reviewPlan = reviewPlan(evidenceReview);
        InterviewBudgetUsage usage = new InterviewBudgetUsage(
                roundRepository.countQuestions(ownerId, interviewId),
                roundRepository.countFollowUps(ownerId, interviewId),
                executionRepository.sumModelCallCount(ownerId, interviewId),
                executionRepository.sumTotalTokens(ownerId, interviewId)
        );

        return supervisor.decide(new InterviewSupervisorInput(
                session,
                usage,
                forecast,
                reviewPlan,
                technicalReviewId,
                evidenceReviewId,
                question.followUpAllowed(),
                !technicalReview.suggestedFollowUp().isBlank(),
                null
        ));
    }

    private static InterviewReviewPlan reviewPlan(EvidenceReview evidenceReview) {
        if (evidenceReview.source() == EvidenceReviewSource.JAVA
                && evidenceReview.verdict() == EvidenceConsistencyVerdict.NOT_APPLICABLE) {
            return InterviewReviewPlan.TECHNICAL_ONLY;
        }
        if (evidenceReview.source() == EvidenceReviewSource.MODEL
                && evidenceReview.verdict() != EvidenceConsistencyVerdict.NOT_APPLICABLE) {
            return InterviewReviewPlan.TECHNICAL_AND_EVIDENCE;
        }
        throw new IllegalStateException("证据评审来源与结论不符合评审计划");
    }

    private static void requireScope(
            ActorId ownerId,
            UUID interviewId,
            int roundNo,
            UUID questionId,
            UUID answerId,
            MockInterviewSession session,
            InterviewRound round,
            InterviewQuestion question,
            InterviewAnswer answer,
            TechnicalReview technicalReview,
            EvidenceReview evidenceReview
    ) {
        if (!session.ownerId().equals(ownerId)
                || !session.interviewId().equals(interviewId)
                || session.status() != InterviewStatus.REVIEWING
                || !round.ownerId().equals(ownerId)
                || !round.interviewId().equals(interviewId)
                || round.roundNo() != roundNo
                || round.status() != InterviewRoundStatus.ANSWERED
                || !question.ownerId().equals(ownerId)
                || !question.interviewId().equals(interviewId)
                || !question.roundId().equals(round.roundId())
                || !question.questionId().equals(questionId)
                || !answer.ownerId().equals(ownerId)
                || !answer.interviewId().equals(interviewId)
                || !answer.roundId().equals(round.roundId())
                || !answer.questionId().equals(questionId)
                || !answer.answerId().equals(answerId)
                || !technicalReview.ownerId().equals(ownerId)
                || !technicalReview.interviewId().equals(interviewId)
                || !technicalReview.roundId().equals(round.roundId())
                || !technicalReview.questionId().equals(questionId)
                || !technicalReview.answerId().equals(answerId)
                || !evidenceReview.ownerId().equals(ownerId)
                || !evidenceReview.interviewId().equals(interviewId)
                || !evidenceReview.roundId().equals(round.roundId())
                || !evidenceReview.questionId().equals(questionId)
                || !evidenceReview.answerId().equals(answerId)) {
            throw new IllegalStateException("Supervisor输入事实的owner、面试、回合、问题或答案作用域不一致");
        }
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}