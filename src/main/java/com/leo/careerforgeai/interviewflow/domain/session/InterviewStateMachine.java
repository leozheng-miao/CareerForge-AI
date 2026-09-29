package com.leo.careerforgeai.interviewflow.domain.session;

import com.leo.careerforgeai.interviewflow.domain.timer.InterviewTimer;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 以纯Java规则控制快速面试状态、计时和追问预算迁移
 * @author: Miao Zheng
 * @date: 2026-09-30
 */
public final class InterviewStateMachine {

    private final QuickInterviewPolicy policy;

    public InterviewStateMachine(QuickInterviewPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy不能为空");
    }

    public TransitionResult transition(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        Objects.requireNonNull(aggregate, "aggregate不能为空");
        Objects.requireNonNull(event, "event不能为空");
        if (aggregate.session().stage().isTerminal()) {
            throw illegalTransition(aggregate, event);
        }

        return switch (event) {
            case PREPARATION_COMPLETED -> preparationCompleted(aggregate, event);
            case FORMAL_START_REQUESTED -> formalStartRequested(aggregate, event);
            case PAUSE_REQUESTED -> pauseRequested(aggregate, event);
            case RESUME_REQUESTED -> resumeRequested(aggregate, event);
            case ANSWER_SUBMITTED -> answerSubmitted(aggregate, event);
            case TIMER_EXHAUSTED -> timerExhausted(aggregate, event);
            case FOLLOW_UP_SELECTED -> followUpSelected(aggregate, event);
            case NEXT_TOPIC_SELECTED -> nextTopicSelected(aggregate, event);
            case FINISH_REQUESTED -> finishRequested(aggregate, event);
            case REPORT_GENERATED -> reportGenerated(aggregate, event);
        };
    }

    private TransitionResult preparationCompleted(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireStage(aggregate, SessionStage.PREPARING, event);
        requireTimerState(aggregate, TimerState.NOT_STARTED, event);
        return TransitionResult.withoutReport(
                aggregate.changeStage(
                        SessionStage.READY,
                        aggregate.timer()
                )
        );
    }

    private TransitionResult formalStartRequested(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireStage(aggregate, SessionStage.READY, event);
        requireTimerState(aggregate, TimerState.NOT_STARTED, event);
        return TransitionResult.withoutReport(
                aggregate.changeStage(
                        SessionStage.INTERVIEWING,
                        aggregate.timer().start()
                )
        );
    }

    private TransitionResult pauseRequested(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireStage(aggregate, SessionStage.INTERVIEWING, event);
        InterviewTimer timer = aggregate.timer();
        if (timer.state() == TimerState.EXHAUSTED) {
            return TransitionResult.withoutReport(
                    aggregate.changeTimer(timer)
            );
        }
        requireTimerState(aggregate, TimerState.RUNNING, event);
        return TransitionResult.withoutReport(
                aggregate.changeTimer(timer.pause())
        );
    }

    private TransitionResult resumeRequested(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireStage(aggregate, SessionStage.INTERVIEWING, event);
        InterviewTimer timer = aggregate.timer();
        if (timer.state() == TimerState.EXHAUSTED) {
            return TransitionResult.withoutReport(
                    aggregate.changeTimer(timer)
            );
        }
        requireTimerState(aggregate, TimerState.PAUSED, event);
        return TransitionResult.withoutReport(
                aggregate.changeTimer(timer.start())
        );
    }

    private TransitionResult answerSubmitted(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireStage(aggregate, SessionStage.INTERVIEWING, event);
        TimerState timerState = aggregate.timer().state();
        if (timerState == TimerState.RUNNING) {
            return TransitionResult.withoutReport(
                    aggregate.recordSubmittedAnswer(
                            SessionStage.INTERVIEWING,
                            aggregate.timer().pause()
                    )
            );
        }
        if (timerState == TimerState.EXHAUSTED) {
            return TransitionResult.withReport(
                    aggregate.recordSubmittedAnswer(
                            SessionStage.GENERATING_REPORT,
                            aggregate.timer().stop()
                    )
            );
        }
        throw illegalTransition(aggregate, event);
    }

    private TransitionResult timerExhausted(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireStage(aggregate, SessionStage.INTERVIEWING, event);
        requireTimerState(aggregate, TimerState.RUNNING, event);
        return TransitionResult.withoutReport(
                aggregate.changeTimer(aggregate.timer().exhaust())
        );
    }

    private TransitionResult followUpSelected(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireWaitingForDecision(aggregate, event);
        if (!policy.allowsAnotherFollowUp(
                aggregate.followUpCount(),
                aggregate.consecutiveFollowUpCount()
        )) {
            throw new IllegalStateException("快速面试追问预算已经耗尽");
        }
        return TransitionResult.withoutReport(
                aggregate.recordFollowUp()
        );
    }

    private TransitionResult nextTopicSelected(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireWaitingForDecision(aggregate, event);
        return TransitionResult.withoutReport(
                aggregate.startNextTopic()
        );
    }

    private TransitionResult finishRequested(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireStage(aggregate, SessionStage.INTERVIEWING, event);
        InterviewTimer stoppedTimer = aggregate.timer().stop();
        if (policy.shouldGenerateReport(
                aggregate.submittedAnswerCount()
        )) {
            return TransitionResult.withReport(
                    aggregate.changeStage(
                            SessionStage.GENERATING_REPORT,
                            stoppedTimer
                    )
            );
        }
        return TransitionResult.withoutReport(
                aggregate.changeStage(
                        SessionStage.COMPLETED,
                        stoppedTimer
                )
        );
    }

    private TransitionResult reportGenerated(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireStage(aggregate, SessionStage.GENERATING_REPORT, event);
        requireTimerState(aggregate, TimerState.STOPPED, event);
        return TransitionResult.withoutReport(
                aggregate.changeStage(
                        SessionStage.COMPLETED,
                        aggregate.timer()
                )
        );
    }

    private void requireWaitingForDecision(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        requireStage(aggregate, SessionStage.INTERVIEWING, event);
        requireTimerState(aggregate, TimerState.PAUSED, event);
        if (aggregate.submittedAnswerCount() <= 0) {
            throw illegalTransition(aggregate, event);
        }
    }

    private void requireStage(
            InterviewAggregate aggregate,
            SessionStage expected,
            InterviewEvent event
    ) {
        if (aggregate.session().stage() != expected) {
            throw illegalTransition(aggregate, event);
        }
    }

    private void requireTimerState(
            InterviewAggregate aggregate,
            TimerState expected,
            InterviewEvent event
    ) {
        if (aggregate.timer().state() != expected) {
            throw illegalTransition(aggregate, event);
        }
    }

    private IllegalStateException illegalTransition(
            InterviewAggregate aggregate,
            InterviewEvent event
    ) {
        return new IllegalStateException(
                "非法面试状态迁移：stage="
                        + aggregate.session().stage()
                        + ", timer="
                        + aggregate.timer().state()
                        + ", event="
                        + event
        );
    }
}
