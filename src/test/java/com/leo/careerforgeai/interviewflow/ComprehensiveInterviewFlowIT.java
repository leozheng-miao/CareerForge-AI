package com.leo.careerforgeai.interviewflow;

import com.leo.careerforgeai.interviewflow.application.preparation.ComprehensiveInterviewBlueprintPlanner;
import com.leo.careerforgeai.interviewflow.application.preparation.ComprehensivePreparationCoordinator.PreparationSignals;
import com.leo.careerforgeai.interviewflow.application.preparation.JdSignalAnalyzer.JdSignals;
import com.leo.careerforgeai.interviewflow.application.preparation.ProfileEvidenceAnalyzer.ExperienceSignal;
import com.leo.careerforgeai.interviewflow.application.preparation.ProfileEvidenceAnalyzer.ProfileSignals;
import com.leo.careerforgeai.interviewflow.domain.session.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class ComprehensiveInterviewFlowIT {
    @Test
    void challengeNinetyMinuteFlowKeepsSixThemeBreadthAndEightFollowUpBudget() {
        var planner = new ComprehensiveInterviewBlueprintPlanner();
        var blueprint = planner.plan(UUID.randomUUID(), signalsWithProfile());
        var policy = new ComprehensiveInterviewPolicy(5400);
        var machine = new InterviewStateMachine(policy);
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        var aggregate = InterviewAggregate.createComprehensive(UUID.randomUUID(),
                new ActorId("owner"), now, policy);
        aggregate = machine.transition(aggregate, InterviewEvent.PREPARATION_COMPLETED, now).aggregate();
        aggregate = machine.transition(aggregate, InterviewEvent.FORMAL_START_REQUESTED, now).aggregate();

        assertThat(blueprint.themes()).hasSize(6);
        assertThat(blueprint.themes().stream()
                .filter(t -> t.code().startsWith("EXPERIENCE_")).toList())
                .hasSizeBetween(2, 3);
        assertThat(policy.maxFollowUps()).isEqualTo(8);
        assertThat(aggregate.session().stage()).isEqualTo(SessionStage.INTERVIEWING);
    }

    @Test
    void noResumeBranchStillProducesSixJdAndPlatformThemes() {
        var blueprint = new ComprehensiveInterviewBlueprintPlanner().plan(UUID.randomUUID(),
                new PreparationSignals(new JdSignals(List.of("Agent工作流", "RAG"),
                        "生产实践", "25K-35K"), ProfileSignals.empty(), List.of()));
        assertThat(blueprint.themes()).hasSize(6);
        assertThat(blueprint.themes()).noneMatch(t -> t.code().startsWith("EXPERIENCE_"));
    }

    private static PreparationSignals signalsWithProfile() {
        List<ExperienceSignal> experiences = List.of(
                new ExperienceSignal("项目", "Agent订单系统", "后端", "实现幂等任务", List.of("Java")),
                new ExperienceSignal("实习", "知识库", "开发", "实现RAG", List.of("RAG")),
                new ExperienceSignal("项目", "评测平台", "负责人", "构建回归集", List.of("Tracing")));
        return new PreparationSignals(new JdSignals(List.of("Agent工作流", "Java后端"),
                "复杂约束、架构权衡与量化验证", "40K-60K"),
                new ProfileSignals("", List.of("Java"), experiences), List.of());
    }
}
