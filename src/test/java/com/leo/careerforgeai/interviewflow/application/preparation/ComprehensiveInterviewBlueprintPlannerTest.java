package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.application.preparation.ComprehensivePreparationCoordinator.PreparationSignals;
import com.leo.careerforgeai.interviewflow.application.preparation.JdSignalAnalyzer.JdSignals;
import com.leo.careerforgeai.interviewflow.application.preparation.ProfileEvidenceAnalyzer.ExperienceSignal;
import com.leo.careerforgeai.interviewflow.application.preparation.ProfileEvidenceAnalyzer.ProfileSignals;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ComprehensiveInterviewBlueprintPlannerTest {
    private final ComprehensiveInterviewBlueprintPlanner planner =
            new ComprehensiveInterviewBlueprintPlanner();

    @Test
    void richProfileStillKeepsAtLeastThreeJobCoreThemes() {
        List<ExperienceSignal> experiences = List.of(
                experience("订单Agent"), experience("知识库"),
                experience("评测平台"), experience("客服Agent"));
        var blueprint = planner.plan(UUID.randomUUID(), new PreparationSignals(
                new JdSignals(List.of("Agent工作流", "Java后端"), "复杂架构权衡", "40-60K"),
                new ProfileSignals("", List.of("Java"), experiences), List.of()));

        assertThat(blueprint.themes()).hasSize(6);
        assertThat(blueprint.themes().stream()
                .filter(theme -> theme.code().startsWith("EXPERIENCE_")).toList()).hasSize(3);
        assertThat(blueprint.topics()).extracting(topic -> topic.sequenceNo())
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(blueprint.themes().getFirst().question())
                .contains("确认画像中提到").doesNotContain("已经证明");
    }

    @Test
    void noProfileBuildsSixPlatformAndJdThemes() {
        var blueprint = planner.plan(UUID.randomUUID(), new PreparationSignals(
                new JdSignals(List.of("RAG检索"), "生产实践", ""),
                ProfileSignals.empty(), List.of()));
        assertThat(blueprint.themes()).hasSize(6);
        assertThat(blueprint.themes()).noneMatch(
                theme -> theme.code().startsWith("EXPERIENCE_"));
    }

    private static ExperienceSignal experience(String label) {
        return new ExperienceSignal("项目", label, "开发", "用户确认的描述",
                List.of("Java"));
    }
}
