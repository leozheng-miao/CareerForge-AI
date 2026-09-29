package com.leo.careerforgeai.interviewflow.domain.session;

import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证快速面试纯Java状态机的合法迁移、计时和结束不变量
 * @author: Miao Zheng
 * @date: 2026-09-28
 */
class InterviewStateMachineTest {

    private static final UUID INTERVIEW_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final ActorId OWNER_ID = new ActorId("quick-interview-owner");
    private static final Instant NOW = Instant.parse("2026-09-28T04:00:00Z");

    private final QuickInterviewPolicy policy = new QuickInterviewPolicy();
    private final InterviewStateMachine stateMachine =
            new InterviewStateMachine(policy);

    @Test
    void shouldCompleteTheLegalMainPathAndRequestReportForSubmittedAnswer() {
        InterviewAggregate aggregate = newQuickInterview();

        aggregate = transition(
                aggregate,
                InterviewEvent.PREPARATION_COMPLETED
        ).aggregate();
        assertThat(aggregate.session().stage()).isEqualTo(SessionStage.READY);
        assertThat(aggregate.timer().state()).isEqualTo(TimerState.NOT_STARTED);

        aggregate = transition(
                aggregate,
                InterviewEvent.FORMAL_START_REQUESTED
        ).aggregate();
        assertThat(aggregate.session().stage()).isEqualTo(SessionStage.INTERVIEWING);
        assertThat(aggregate.timer().state()).isEqualTo(TimerState.RUNNING);

        aggregate = transition(
                aggregate,
                InterviewEvent.ANSWER_SUBMITTED
        ).aggregate();
        assertThat(aggregate.submittedAnswerCount()).isEqualTo(1);
        assertThat(aggregate.timer().state()).isEqualTo(TimerState.PAUSED);

        TransitionResult finishResult = transition(
                aggregate,
                InterviewEvent.FINISH_REQUESTED
        );
        assertThat(finishResult.aggregate().session().stage())
                .isEqualTo(SessionStage.GENERATING_REPORT);
        assertThat(finishResult.shouldGenerateReport()).isTrue();

        TransitionResult completedResult = transition(
                finishResult.aggregate(),
                InterviewEvent.REPORT_GENERATED
        );
        assertThat(completedResult.aggregate().session().stage())
                .isEqualTo(SessionStage.COMPLETED);
        assertThat(completedResult.aggregate().timer().state())
                .isEqualTo(TimerState.STOPPED);
    }

    @ParameterizedTest
    @EnumSource(
            value = InterviewEvent.class,
            names = {
                    "FORMAL_START_REQUESTED",
                    "PAUSE_REQUESTED",
                    "RESUME_REQUESTED",
                    "ANSWER_SUBMITTED",
                    "TIMER_EXHAUSTED",
                    "REPORT_GENERATED"
            }
    )
    void shouldRejectEventsThatAreIllegalWhilePreparing(
            InterviewEvent event
    ) {
        InterviewAggregate aggregate = newQuickInterview();

        assertThatThrownBy(() -> transition(aggregate, event))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldPauseAndResumeWithoutChangingTheInterviewStage() {
        InterviewAggregate aggregate = enterInterview();

        aggregate = transition(
                aggregate,
                InterviewEvent.PAUSE_REQUESTED
        ).aggregate();
        assertThat(aggregate.session().stage()).isEqualTo(SessionStage.INTERVIEWING);
        assertThat(aggregate.timer().state()).isEqualTo(TimerState.PAUSED);

        aggregate = transition(
                aggregate,
                InterviewEvent.RESUME_REQUESTED
        ).aggregate();
        assertThat(aggregate.session().stage()).isEqualTo(SessionStage.INTERVIEWING);
        assertThat(aggregate.timer().state()).isEqualTo(TimerState.RUNNING);
    }

    @Test
    void shouldKeepCurrentQuestionOpenWhenTimeIsExhaustedAndFinishAfterItsAnswer() {
        InterviewAggregate aggregate = enterInterview();

        TransitionResult exhaustedResult = transition(
                aggregate,
                InterviewEvent.TIMER_EXHAUSTED
        );
        assertThat(exhaustedResult.aggregate().session().stage())
                .isEqualTo(SessionStage.INTERVIEWING);
        assertThat(exhaustedResult.aggregate().timer().state())
                .isEqualTo(TimerState.EXHAUSTED);
        assertThat(exhaustedResult.shouldGenerateReport()).isFalse();

        TransitionResult answerResult = transition(
                exhaustedResult.aggregate(),
                InterviewEvent.ANSWER_SUBMITTED
        );
        assertThat(answerResult.aggregate().submittedAnswerCount()).isEqualTo(1);
        assertThat(answerResult.aggregate().session().stage())
                .isEqualTo(SessionStage.GENERATING_REPORT);
        assertThat(answerResult.shouldGenerateReport()).isTrue();
    }

    @Test
    void shouldRejectAThirdConsecutiveFollowUpAndAFifthTotalFollowUp() {
        InterviewAggregate aggregate = submitCurrentAnswer(enterInterview());

        aggregate = selectFollowUp(aggregate);
        aggregate = selectFollowUp(aggregate);

        InterviewAggregate consecutiveLimitReached = aggregate;
        assertThat(consecutiveLimitReached.followUpCount()).isEqualTo(2);
        assertThat(consecutiveLimitReached.consecutiveFollowUpCount()).isEqualTo(2);
        assertThatThrownBy(() -> selectFollowUp(consecutiveLimitReached))
                .isInstanceOf(IllegalStateException.class);

        aggregate = transition(
                aggregate,
                InterviewEvent.NEXT_TOPIC_SELECTED
        ).aggregate();
        assertThat(aggregate.consecutiveFollowUpCount()).isZero();

        aggregate = selectFollowUp(aggregate);
        aggregate = selectFollowUp(aggregate);

        InterviewAggregate totalLimitReached = transition(
                aggregate,
                InterviewEvent.NEXT_TOPIC_SELECTED
        ).aggregate();
        assertThat(totalLimitReached.followUpCount()).isEqualTo(4);
        assertThatThrownBy(() -> selectFollowUp(totalLimitReached))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCompleteWithoutReportWhenUserFinishesBeforeSubmittingAnyAnswer() {
        InterviewAggregate aggregate = enterInterview();

        TransitionResult result = transition(
                aggregate,
                InterviewEvent.FINISH_REQUESTED
        );

        assertThat(result.aggregate().session().stage())
                .isEqualTo(SessionStage.COMPLETED);
        assertThat(result.aggregate().timer().state())
                .isEqualTo(TimerState.STOPPED);
        assertThat(result.shouldGenerateReport()).isFalse();
    }

    @Test
    void shouldRejectAnyFurtherTransitionAfterCompletion() {
        InterviewAggregate aggregate = enterInterview();
        aggregate = transition(
                aggregate,
                InterviewEvent.FINISH_REQUESTED
        ).aggregate();

        InterviewAggregate completed = aggregate;
        assertThatThrownBy(() -> transition(
                completed,
                InterviewEvent.FORMAL_START_REQUESTED
        )).isInstanceOf(IllegalStateException.class);
    }

    private InterviewAggregate newQuickInterview() {
        return InterviewAggregate.createQuick(
                INTERVIEW_ID,
                OWNER_ID,
                NOW,
                policy
        );
    }

    private InterviewAggregate enterInterview() {
        InterviewAggregate aggregate = newQuickInterview();
        aggregate = transition(
                aggregate,
                InterviewEvent.PREPARATION_COMPLETED
        ).aggregate();
        return transition(
                aggregate,
                InterviewEvent.FORMAL_START_REQUESTED
        ).aggregate();
    }

    private InterviewAggregate submitCurrentAnswer(
            InterviewAggregate aggregate
    ) {
        return transition(
                aggregate,
                InterviewEvent.ANSWER_SUBMITTED
        ).aggregate();
    }

    private InterviewAggregate selectFollowUp(
            InterviewAggregate aggregate
    ) {
        return transition(
                aggregate,
                InterviewEvent.FOLLOW_UP_SELECTED
        ).aggregate();
    }

    private TransitionResult transition(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        return stateMachine.transition(aggregate, event);
    }
}
