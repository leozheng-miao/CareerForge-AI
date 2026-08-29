package com.leo.careerforgeai.interview.application.supervision;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
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
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 重新读取MySQL事实并原子应用Java Supervisor的回合路由决策
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Service
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        InterviewRoundRepository.class
})
public class InterviewRouteApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewRoundRepository roundRepository;
    private final Clock clock;

    public InterviewRouteApplicationService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewRoundRepository roundRepository,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public MockInterviewSession apply(
            UUID interviewId,
            int roundNo,
            InterviewRouteDecision routeDecision,
            InterviewFailureCode failureCode
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(routeDecision, "routeDecision不能为空");
        if (roundNo < 1) throw new IllegalArgumentException("roundNo必须从1开始");
        boolean failureRoute = routeDecision == InterviewRouteDecision.FINALIZE_FAILURE;
        if (failureRoute != (failureCode != null)) {
            throw new IllegalArgumentException("failureCode与routeDecision不匹配");
        }

        ActorId ownerId = currentActor();
        MockInterviewSession session = sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
        InterviewRound round = roundRepository.findRoundByNumber(ownerId, interviewId, roundNo)
                .orElseThrow(() -> new IllegalStateException("MySQL缺少当前面试回合"));

        requireScope(ownerId, interviewId, roundNo, session, round);
        if (isReplay(session, round, routeDecision, failureCode)) return session;
        if (session.status() != InterviewStatus.REVIEWING
                || round.status() != InterviewRoundStatus.ANSWERED) {
            throw new IllegalStateException("只有REVIEWING面试和ANSWERED回合可以应用Supervisor路由");
        }

        Instant now = clock.instant();
        InterviewRound reviewed = round.review(now);
        MockInterviewSession routed = route(session, routeDecision, failureCode, now);

        if (!roundRepository.updateRoundIfVersionMatches(ownerId, reviewed, round.version())) {
            throw new IllegalStateException("Supervisor回合CAS更新失败");
        }
        if (!sessionRepository.updateIfVersionMatches(ownerId, routed, session.version())) {
            throw new MockInterviewVersionConflictException(interviewId, session.version());
        }
        return routed;
    }

    private static MockInterviewSession route(
            MockInterviewSession session,
            InterviewRouteDecision routeDecision,
            InterviewFailureCode failureCode,
            Instant now
    ) {
        return switch (routeDecision) {
            case FOLLOW_UP, NEXT_QUESTION -> session.continueQuestioning(now);
            case GENERATE_REPORT -> session.startReportGeneration(now);
            case FINALIZE_FAILURE -> session.fail(failureCode, now);
        };
    }

    private static boolean isReplay(
            MockInterviewSession session,
            InterviewRound round,
            InterviewRouteDecision routeDecision,
            InterviewFailureCode failureCode
    ) {
        if (round.status() != InterviewRoundStatus.REVIEWED) return false;
        return switch (routeDecision) {
            case FOLLOW_UP, NEXT_QUESTION -> session.status() == InterviewStatus.GENERATING_QUESTION;
            case GENERATE_REPORT -> session.status() == InterviewStatus.GENERATING_REPORT;
            case FINALIZE_FAILURE ->
                    session.status() == InterviewStatus.FAILED
                            && session.failureCode() == failureCode;
        };
    }

    private static void requireScope(
            ActorId ownerId,
            UUID interviewId,
            int roundNo,
            MockInterviewSession session,
            InterviewRound round
    ) {
        if (!session.ownerId().equals(ownerId)
                || !session.interviewId().equals(interviewId)
                || !round.ownerId().equals(ownerId)
                || !round.interviewId().equals(interviewId)
                || round.roundNo() != roundNo) {
            throw new IllegalStateException("Supervisor路由的owner、面试或回合作用域不一致");
        }
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}