package com.leo.careerforgeai.interview.application.blueprint;

import com.leo.careerforgeai.interview.domain.InterviewBlueprint;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证确定性面试蓝图的题型、难度、技能覆盖和预算边界
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
class InterviewBlueprintPlannerTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private final InterviewBlueprintPlanner planner = new InterviewBlueprintPlanner();

    @Test
    void shouldGenerateSameTargetedBlueprintForSameFrozenInput() {
        MockInterviewSession session = session(
                InterviewMode.TARGETED_MOCK,
                new InterviewBudgetPolicy(5, 2, 20, 20_000)
        );

        InterviewBlueprint first = planner.plan(
                session,
                List.of(" Java ", "Redis", "java"),
                List.of("Agent可靠性"),
                true
        );
        InterviewBlueprint second = planner.plan(
                session,
                List.of(" Java ", "Redis", "java"),
                List.of("Agent可靠性"),
                true
        );

        assertThat(first).isEqualTo(second);
        assertThat(first.inputSnapshotHash()).isEqualTo(session.inputSnapshotHash());
        assertThat(first.questionPlans().stream()
                .map(InterviewBlueprint.QuestionPlan::questionType)
                .toList())
                .containsExactly(
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                        InterviewQuestionType.PROJECT_DEEP_DIVE,
                        InterviewQuestionType.SYSTEM_DESIGN,
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                        InterviewQuestionType.PROJECT_DEEP_DIVE
                );
        assertThat(first.questionPlans().stream()
                .map(InterviewBlueprint.QuestionPlan::difficulty)
                .toList())
                .containsExactly(2, 2, 3, 3, 4);
        assertThat(first.questionPlans().stream()
                .map(plan -> plan.targetSkills().getFirst())
                .toList())
                .containsExactly("Java", "Redis", "Agent可靠性", "Java", "Redis");
        assertThat(first.questionAt(2).evidencePreferred()).isTrue();
    }

    @Test
    void shouldUseOnlyGapSkillsAndSkipProjectQuestionsWithoutProjectEvidence() {
        MockInterviewSession session = session(
                InterviewMode.GAP_DRILL,
                new InterviewBudgetPolicy(4, 1, 16, 16_000)
        );

        InterviewBlueprint blueprint = planner.plan(
                session,
                List.of("Java", "MySQL"),
                List.of("Redis", "Agent可靠性"),
                false
        );

        assertThat(blueprint.questionPlans().stream()
                .map(InterviewBlueprint.QuestionPlan::questionType)
                .toList())
                .containsExactly(
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                        InterviewQuestionType.SYSTEM_DESIGN,
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                        InterviewQuestionType.SYSTEM_DESIGN
                );
        assertThat(blueprint.questionPlans().stream()
                .map(plan -> plan.targetSkills().getFirst())
                .toList())
                .containsExactly("Redis", "Agent可靠性", "Redis", "Agent可靠性");
        assertThat(blueprint.questionPlans())
                .allSatisfy(plan -> assertThat(plan.evidencePreferred()).isFalse());
    }

    @Test
    void shouldRejectMissingGapAndExcessiveQuestionBudget() {
        MockInterviewSession gapSession = session(
                InterviewMode.GAP_DRILL,
                new InterviewBudgetPolicy(4, 1, 16, 16_000)
        );
        MockInterviewSession excessiveSession = session(
                InterviewMode.TARGETED_MOCK,
                new InterviewBudgetPolicy(21, 2, 30, 30_000)
        );

        assertThatThrownBy(() -> planner.plan(
                gapSession,
                List.of("Java"),
                List.of(),
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gapSkills不能为空");

        assertThatThrownBy(() -> planner.plan(
                excessiveSession,
                List.of("Java"),
                List.of(),
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxQuestions不能超过20");
    }

    private MockInterviewSession session(
            InterviewMode mode,
            InterviewBudgetPolicy budgetPolicy
    ) {
        return MockInterviewSession.create(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new ActorId("blueprint-owner"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "a".repeat(64),
                mode,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "b".repeat(64),
                budgetPolicy,
                NOW
        );
    }
}