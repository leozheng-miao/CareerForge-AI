package com.leo.careerforgeai.interviewflow.application.turn;

import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.FinalDecision;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.ComprehensiveInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;
import com.leo.careerforgeai.interviewflow.domain.turn.AnswerStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Java策略门拥有追问预算、终止条件和保守降级最终决定权。 */
@Component
public final class TurnDecisionPolicy {

    private static final int MIN_QUESTION_CHARS = 10;
    private static final int MAX_QUESTION_CHARS = 2_000;

    public FinalDecision decide(
            TurnDecisionProposal proposal,
            InterviewAggregate aggregate,
            AnswerStatus answerStatus,
            boolean hasNextTopic,
            boolean sourcesValid
    ) {
        Objects.requireNonNull(aggregate, "aggregate不能为空");
        Objects.requireNonNull(answerStatus, "answerStatus不能为空");
        if (aggregate.timer().state() == TimerState.EXHAUSTED
                || !hasNextTopic) {
            return FinalDecision.FINISH;
        }
        if (answerStatus == AnswerStatus.DISCARDED || proposal == null) {
            return FinalDecision.NEXT_TOPIC;
        }
        if (proposal.decision() == FinalDecision.FOLLOW_UP
                && answerStatus != AnswerStatus.DISCARDED
                && policyFor(aggregate).allowsAnotherFollowUp(
                        aggregate.followUpCount(),
                        aggregate.consecutiveFollowUpCount()
                )
                && validQuestion(proposal.question())
                && sourcesValid) {
            return FinalDecision.FOLLOW_UP;
        }
        if (proposal.decision() == FinalDecision.FINISH) {
            return FinalDecision.NEXT_TOPIC;
        }
        return FinalDecision.NEXT_TOPIC;
    }

    private static InterviewPolicy policyFor(InterviewAggregate aggregate) {
        return aggregate.session().mode() == InterviewMode.COMPREHENSIVE
                ? new ComprehensiveInterviewPolicy(aggregate.timer().duration().toSeconds())
                : new QuickInterviewPolicy();
    }

    private static boolean validQuestion(String question) {
        return question != null
                && question.length() >= MIN_QUESTION_CHARS
                && question.length() <= MAX_QUESTION_CHARS
                && question.chars().noneMatch(Character::isISOControl);
    }
}
