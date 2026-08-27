package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;

/**
 * @program: CareerForge-AI
 * @description: 使用短事务认领首题生成权并原子保存问题和等待回答状态
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
@Service
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        InterviewRoundRepository.class
})
public class InterviewQuestionPersistenceService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewRoundRepository roundRepository;
    private final InterviewQuestionFactory questionFactory;
    private final Clock clock;

    public InterviewQuestionPersistenceService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewRoundRepository roundRepository,
            InterviewQuestionFactory questionFactory,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.questionFactory = Objects.requireNonNull(questionFactory, "questionFactory不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public Optional<InterviewQuestion> startFirstQuestionGeneration(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);
        Optional<InterviewQuestion> existing = findFirstQuestion(ownerId, interviewId);

        if (existing.isPresent()) {
            if (session.status() == InterviewStatus.CREATED) {
                throw new IllegalStateException("CREATED面试不能已经存在首题");
            }
            if (session.status() == InterviewStatus.GENERATING_QUESTION) {
                updateSession(ownerId, session, session.waitForAnswer(clock.instant()));
            }
            return existing;
        }

        if (session.status() != InterviewStatus.CREATED) {
            throw new IllegalStateException("首题不存在时只有CREATED面试可以开始生成");
        }

        updateSession(ownerId, session, session.startQuestionGeneration(clock.instant()));
        return Optional.empty();
    }

    @Transactional
    public InterviewQuestion persistFirstQuestion(
            UUID interviewId,
            InterviewRoleModelGateway.Result<InterviewQuestionDraft> result
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(result, "result不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);
        Optional<InterviewQuestion> existing = findFirstQuestion(ownerId, interviewId);

        if (existing.isPresent()) {
            if (session.status() == InterviewStatus.GENERATING_QUESTION) {
                updateSession(ownerId, session, session.waitForAnswer(clock.instant()));
            }
            return existing.get();
        }
        if (session.status() != InterviewStatus.GENERATING_QUESTION) {
            throw new IllegalStateException("只有GENERATING_QUESTION状态可以保存首题");
        }

        Instant now = clock.instant();
        UUID roundId = UUID.randomUUID();
        InterviewRound round = InterviewRound.questionReady(roundId, interviewId, ownerId, 1, now);
        InterviewQuestion question = questionFactory.createFirstQuestion(
                UUID.randomUUID(),
                interviewId,
                roundId,
                ownerId,
                result,
                now
        );
        InterviewQuestion stored = roundRepository.claimQuestionReadyRound(round, question);
        updateSession(ownerId, session, session.waitForAnswer(now));
        return stored;
    }

    @Transactional
    public void failFirstQuestionGeneration(
            UUID interviewId,
            InterviewFailureCode failureCode
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(failureCode, "failureCode不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);

        if (session.isTerminal()) return;
        if (session.status() != InterviewStatus.GENERATING_QUESTION) {
            throw new IllegalStateException("只有GENERATING_QUESTION状态可以收敛首题生成失败");
        }

        updateSession(ownerId, session, session.fail(failureCode, clock.instant()));
    }

    private Optional<InterviewQuestion> findFirstQuestion(ActorId ownerId, UUID interviewId) {
        return roundRepository.findRoundByNumber(ownerId, interviewId, 1)
                .map(round -> roundRepository.findQuestionByRound(ownerId, interviewId, round.roundId())
                        .orElseThrow(() -> new IllegalStateException("首回合存在但缺少问题事实")));
    }

    private MockInterviewSession requireSession(ActorId ownerId, UUID interviewId) {
        return sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
    }

    private void updateSession(
            ActorId ownerId,
            MockInterviewSession current,
            MockInterviewSession updated
    ) {
        if (!sessionRepository.updateIfVersionMatches(ownerId, updated, current.version())) {
            throw new MockInterviewVersionConflictException(current.interviewId(), current.version());
        }
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}