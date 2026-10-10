package com.leo.careerforgeai.interviewflow.application.turn;

import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.FinalDecision;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.timer.InterviewTimer;
import com.leo.careerforgeai.interviewflow.domain.turn.AnswerStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ComprehensiveTurnDecisionTest {
    private final TurnDecisionPolicy policy = new TurnDecisionPolicy();
    private final TurnDecisionProposal followUp = new TurnDecisionProposal(
            FinalDecision.FOLLOW_UP, "需要验证", "请进一步说明具体实现和验证结果。",
            List.of("chunk"), false);

    @Test
    void ninetyMinuteModeAllowsEightButNeverThreeConsecutiveFollowUps() {
        assertThat(policy.decide(followUp, aggregate(5400, 7, 1),
                AnswerStatus.CLOSED, true, true)).isEqualTo(FinalDecision.FOLLOW_UP);
        assertThat(policy.decide(followUp, aggregate(5400, 7, 2),
                AnswerStatus.CLOSED, true, true)).isEqualTo(FinalDecision.NEXT_TOPIC);
    }

    @Test
    void sixtyMinuteModeStopsAtFourFollowUps() {
        assertThat(policy.decide(followUp, aggregate(3600, 4, 1),
                AnswerStatus.CLOSED, true, true)).isEqualTo(FinalDecision.NEXT_TOPIC);
    }

    private static InterviewAggregate aggregate(long seconds, int followUps,
            int consecutive) {
        return new InterviewAggregate(new InterviewSession(UUID.randomUUID(),
                new ActorId(UUID.randomUUID().toString()), InterviewMode.COMPREHENSIVE,
                SessionStage.INTERVIEWING, 1),
                InterviewTimer.notStarted(Duration.ofSeconds(seconds)), 1,
                followUps, consecutive, Instant.now());
    }
}
