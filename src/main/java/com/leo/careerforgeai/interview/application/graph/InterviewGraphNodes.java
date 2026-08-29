package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.question.InterviewQuestionGenerationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 实现面试Graph冻结上下文校验、任意回合问题生成和答案恢复校验
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Component
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        InterviewRoundRepository.class,
        InterviewQuestionGenerationService.class
})
public class InterviewGraphNodes {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewRoundRepository roundRepository;
    private final InterviewQuestionGenerationService questionGenerationService;
    private final Duration modelCallTimeout;

    public InterviewGraphNodes(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewRoundRepository roundRepository,
            InterviewQuestionGenerationService questionGenerationService,
            @Value("${careerforge.agent.loop.model-call-timeout}") Duration modelCallTimeout
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.questionGenerationService = Objects.requireNonNull(questionGenerationService, "questionGenerationService不能为空");
        if (modelCallTimeout == null || modelCallTimeout.isZero() || modelCallTimeout.isNegative()) {
            throw new IllegalArgumentException("modelCallTimeout必须大于0");
        }
        this.modelCallTimeout = modelCallTimeout;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadFrozenContext(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = sessionRepository.findById(ownerId, state.interviewId())
                .orElseThrow(() -> new MockInterviewNotFoundException(state.interviewId()));

        if (!session.ownerId().equals(ownerId)
                || session.mode() != state.mode()
                || !session.inputSnapshotHash().equals(state.inputSnapshotHash())) {
            throw new MockInterviewInputConflictException();
        }
        if (session.status() != InterviewStatus.CREATED
                && session.status() != InterviewStatus.GENERATING_QUESTION
                && session.status() != InterviewStatus.WAITING_FOR_ANSWER) {
            throw new IllegalStateException("当前Session状态不能进入问题生成Graph");
        }
        return Map.of();
    }

    public Map<String, Object> generateAndPersistQuestion(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        if (state.currentRound() == Integer.MAX_VALUE) throw new IllegalStateException("面试回合号已经超出允许范围");

        int nextRound = state.currentRound() + 1;
        InterviewRouteDecision routeDecision = requireGenerationRoute(state, nextRound);
        InterviewQuestion question = questionGenerationService.generateAndPersistQuestion(
                state.interviewId(), nextRound, routeDecision, modelCallTimeout
        );

        if (!question.interviewId().equals(state.interviewId())) {
            throw new IllegalStateException("生成的问题不属于当前面试");
        }
        return nextRound == 1
                ? InterviewGraphState.waitingForAnswerUpdate(nextRound, question.questionId())
                : InterviewGraphState.waitingForNextAnswerUpdate(nextRound, question.questionId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validateAnswerResume(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        UUID questionId = state.currentQuestionId()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少currentQuestionId"));
        UUID answerId = state.answerId()
                .orElseThrow(() -> new IllegalStateException("resume缺少answerId"));
        if (state.currentRound() < 1) throw new IllegalStateException("Checkpoint尚未进入有效回合");

        ActorId ownerId = currentActor();
        MockInterviewSession session = sessionRepository.findById(ownerId, state.interviewId())
                .orElseThrow(() -> new MockInterviewNotFoundException(state.interviewId()));

        if (!session.ownerId().equals(ownerId)
                || session.mode() != state.mode()
                || !session.inputSnapshotHash().equals(state.inputSnapshotHash())) {
            throw new MockInterviewInputConflictException();
        }
        if (session.status() != InterviewStatus.REVIEWING) {
            throw new IllegalStateException("只有已提交答案并进入REVIEWING的面试可以恢复");
        }

        InterviewRound round = roundRepository.findRoundByNumber(ownerId, state.interviewId(), state.currentRound())
                .orElseThrow(() -> new IllegalStateException("MySQL缺少当前面试回合"));
        if (round.status() != InterviewRoundStatus.ANSWERED) {
            throw new IllegalStateException("当前回合尚未完成答案提交");
        }

        InterviewQuestion question = roundRepository.findQuestionByRound(ownerId, state.interviewId(), round.roundId())
                .orElseThrow(() -> new IllegalStateException("MySQL缺少当前问题"));
        if (!question.questionId().equals(questionId)) {
            throw new IllegalStateException("Checkpoint问题与MySQL当前问题不一致");
        }

        InterviewAnswer answer = roundRepository.findAnswerByQuestion(ownerId, state.interviewId(), questionId)
                .orElseThrow(() -> new IllegalStateException("MySQL缺少当前问题答案"));
        requireAnswerScope(ownerId, state, round, question, answer, answerId);
        return InterviewGraphState.clearWaitReasonUpdate();
    }

    private InterviewRouteDecision requireGenerationRoute(InterviewGraphState state, int nextRound) {
        if (nextRound == 1) {
            if (state.routeDecision().isPresent()) throw new IllegalStateException("首题不能包含routeDecision");
            return null;
        }

        InterviewRouteDecision routeDecision = state.routeDecision()
                .orElseThrow(() -> new IllegalStateException("后续问题生成缺少routeDecision"));
        if (routeDecision != InterviewRouteDecision.FOLLOW_UP
                && routeDecision != InterviewRouteDecision.NEXT_QUESTION) {
            throw new IllegalStateException("后续问题只能来自FOLLOW_UP或NEXT_QUESTION");
        }
        return routeDecision;
    }

    private void requireAnswerScope(
            ActorId ownerId,
            InterviewGraphState state,
            InterviewRound round,
            InterviewQuestion question,
            InterviewAnswer answer,
            UUID expectedAnswerId
    ) {
        if (!round.ownerId().equals(ownerId)
                || !round.interviewId().equals(state.interviewId())
                || !question.ownerId().equals(ownerId)
                || !question.interviewId().equals(state.interviewId())
                || !question.roundId().equals(round.roundId())
                || !answer.answerId().equals(expectedAnswerId)
                || !answer.ownerId().equals(ownerId)
                || !answer.interviewId().equals(state.interviewId())
                || !answer.roundId().equals(round.roundId())
                || !answer.questionId().equals(question.questionId())) {
            throw new IllegalStateException("答案、问题、回合或owner作用域不一致");
        }
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}