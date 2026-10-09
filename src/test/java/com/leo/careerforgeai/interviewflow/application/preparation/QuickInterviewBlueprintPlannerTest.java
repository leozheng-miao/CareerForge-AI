package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.api.session.InterviewSessionResponse;
import com.leo.careerforgeai.interviewflow.domain.topic.TopicStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证快速面试固定八目标蓝图和对外隐藏边界
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
class QuickInterviewBlueprintPlannerTest {

    private static final UUID INTERVIEW_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000001"
    );

    @Test
    void shouldPlanExactlyEightStableAndDistinctTargets() {
        QuickInterviewBlueprintPlanner planner =
                new QuickInterviewBlueprintPlanner();

        var first = planner.plan(INTERVIEW_ID);
        var replay = planner.plan(INTERVIEW_ID);

        assertThat(first).isEqualTo(replay);
        assertThat(first.policyVersion())
                .isEqualTo(QuickInterviewPolicyPack.VERSION);
        assertThat(first.targets()).hasSize(8);
        assertThat(first.targets())
                .extracting(target -> target.topic().sequenceNo())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(first.targets())
                .extracting(target -> target.topic().topicId())
                .doesNotHaveDuplicates();
        assertThat(first.targets())
                .extracting(target -> target.topic().objective())
                .doesNotHaveDuplicates();
        assertThat(first.targets())
                .allMatch(target -> target.topic().status()
                        == TopicStatus.PLANNED);
    }

    @Test
    void shouldProvideOneQualifiedSourceBearingBackupForEveryTarget() {
        QuickInterviewPolicyPack policyPack =
                new QuickInterviewPolicyPack();

        assertThat(policyPack.targets()).hasSize(8);
        assertThat(policyPack.targets())
                .extracting(QuickInterviewPolicyPack.TargetPolicy::code)
                .doesNotHaveDuplicates();
        assertThat(policyPack.targets())
                .extracting(
                        QuickInterviewPolicyPack.TargetPolicy::backupQuestion
                )
                .doesNotHaveDuplicates()
                .allMatch(question -> question.length() >= 20);
        assertThat(policyPack.targets())
                .allMatch(target -> target.backupSources().size() == 1)
                .allMatch(target -> target.backupSources().getFirst()
                        .chunkId().matches("[0-9a-f]{64}"));
    }

    @Test
    void shouldNotExposeHiddenTargetsInPublicSessionDto() {
        Set<String> publicFields = Arrays.stream(
                        InterviewSessionResponse.class.getRecordComponents()
                )
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(publicFields).containsExactlyInAnyOrder(
                "interviewId",
                "mode",
                "stage",
                "version"
        );
        assertThat(publicFields).doesNotContain(
                "topics",
                "targets",
                "objective",
                "questions"
        );
    }
}
