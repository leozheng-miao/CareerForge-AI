package com.leo.careerforgeai.interviewflow.application.context;

import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ComprehensiveInterviewContextTest {
    @Test
    void labelsProfileClaimsUntrustedAndGivesAnswersPriority() {
        var context = new InterviewModelContext(ContextPurpose.TURN_DECISION,
                UUID.randomUUID(), new ActorId(UUID.randomUUID().toString()), "AI_AGENT_DEVELOPMENT",
                2, 3, new InterviewModelContext.ActiveTopicContext(UUID.randomUUID(),
                1, "RAG证据", List.of()), List.of(), InterviewMode.COMPREHENSIVE,
                new InterviewModelContext.UntrustedProfileContext(
                        List.of("用户确认技能主张: RAG"), true));

        assertThat(context.mode()).isEqualTo(InterviewMode.COMPREHENSIVE);
        assertThat(context.untrustedProfile().answersHavePriority()).isTrue();
        assertThat(context.untrustedProfile().relevantClaims())
                .containsExactly("用户确认技能主张: RAG");
    }
}
