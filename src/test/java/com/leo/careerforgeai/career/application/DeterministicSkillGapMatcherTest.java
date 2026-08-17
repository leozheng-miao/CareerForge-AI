package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapItem;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapStatus;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.domain.profile.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证确定性能力差距匹配的可信证据、保守降级和owner安全边界
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
class DeterministicSkillGapMatcherTest {
    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final ActorId ACTOR_B = new ActorId("actor-b");
    private static final Instant NOW = Instant.parse("2026-08-16T16:00:00Z");

    private final DeterministicSkillGapMatcher matcher = new DeterministicSkillGapMatcher();

    @Test
    void shouldGenerateMatchedUnverifiedAndMissingWithoutPartialGuessing() {
        MemoryItem javaProject = confirmedSkill(ACTOR_A, "Java", MemorySourceType.PROJECT_EVIDENCE, 1);
        MemoryItem springSelfReport = confirmedSkill(ACTOR_A, "Spring Boot", MemorySourceType.CONVERSATION_TURN, 2);
        ConfirmedSkillProfile profile = new ConfirmedSkillProfile(
                ACTOR_A, 2, List.of(javaProject, springSelfReport));

        List<GapItem> result = matcher.match(targetRole(ACTOR_A, requirements()), profile);
        Map<String, GapItem> byReference = result.stream()
                .collect(Collectors.toMap(GapItem::requirementRef, Function.identity()));

        assertThat(result).hasSize(7);
        assertThat(byReference.get("programmingLanguages[0]").status()).isEqualTo(GapStatus.MATCHED);
        assertThat(byReference.get("programmingLanguages[0]").evidenceMemoryIds())
                .containsExactly(javaProject.memoryId());
        assertThat(byReference.get("backendAndInfrastructureRequirements[0]").status())
                .isEqualTo(GapStatus.UNVERIFIED);
        assertThat(byReference.get("backendAndInfrastructureRequirements[0]").evidenceMemoryIds())
                .containsExactly(springSelfReport.memoryId());
        assertThat(byReference.get("backendAndInfrastructureRequirements[1]").status())
                .isEqualTo(GapStatus.MISSING);
        assertThat(result).noneMatch(item -> item.status() == GapStatus.PARTIAL);
        assertThat(byReference).doesNotContainKeys("responsibilities[0]", "interviewTopics[0]");
    }

    @Test
    void shouldGenerateAllMissingForEmptySkillProfile() {
        ConfirmedSkillProfile profile = new ConfirmedSkillProfile(ACTOR_A, 0, List.of());

        List<GapItem> result = matcher.match(targetRole(ACTOR_A, requirements()), profile);

        assertThat(result).hasSize(7);
        assertThat(result).allMatch(item -> item.status() == GapStatus.MISSING);
        assertThat(result).allMatch(item -> item.evidenceMemoryIds().isEmpty());
    }

    @Test
    void shouldNotUseContainingTextAsExactSkillEvidence() {
        MemoryItem evidence = confirmedSkill(
                ACTOR_A, "Spring Boot", MemorySourceType.PROJECT_EVIDENCE, 1);
        JobRequirements requirements = new JobRequirements(
                "Java工程师",
                List.of(),
                List.of("熟练掌握Spring Boot并完成高并发系统开发"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        List<GapItem> result = matcher.match(
                targetRole(ACTOR_A, requirements),
                new ConfirmedSkillProfile(ACTOR_A, 1, List.of(evidence))
        );

        assertThat(result).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo(GapStatus.MISSING));
    }

    @Test
    void shouldFailClosedWhenTargetAndProfileOwnersDiffer() {
        ConfirmedSkillProfile profile = new ConfirmedSkillProfile(ACTOR_B, 0, List.of());

        assertThatThrownBy(() -> matcher.match(targetRole(ACTOR_A, requirements()), profile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TargetRole与技能画像owner不一致");
    }

    @Test
    void shouldRejectTargetWithoutEvaluableSkillRequirements() {
        JobRequirements empty = new JobRequirements(
                "Java工程师",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of("开发后端服务"), List.of("Java基础")
        );

        assertThatThrownBy(() -> matcher.match(
                targetRole(ACTOR_A, empty),
                new ConfirmedSkillProfile(ACTOR_A, 0, List.of())
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("目标岗位没有可评估的技能要求");
    }

    private TargetRole targetRole(ActorId ownerId, JobRequirements requirements) {
        return TargetRole.createConfirmed(
                UUID.randomUUID(), ownerId, 1, "jd-001", "a".repeat(64),
                "job-requirements-parser-v1", "job-requirements-prompt-v1",
                requirements, NOW
        );
    }

    private JobRequirements requirements() {
        return new JobRequirements(
                "Java Agent开发工程师",
                List.of("Java"),
                List.of("Spring Boot", "MySQL"),
                List.of("Agent状态管理"),
                List.of("RAG"),
                List.of("JUnit 5"),
                List.of("Docker"),
                List.of("开发后端服务"),
                List.of("Java", "Spring Boot")
        );
    }

    private MemoryItem confirmedSkill(ActorId ownerId, String skill,
                                      MemorySourceType sourceType, long secondOffset) {
        UUID memoryId = UUID.randomUUID();
        String sourceId = sourceType.name().toLowerCase() + "-" + memoryId;
        MemoryItem pending = MemoryItem.createPending(
                memoryId,
                ownerId,
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence(skill),
                "已使用" + skill + "完成项目开发",
                new MemorySource(sourceType, sourceId, "b".repeat(64)),
                List.of(sourceId),
                NOW
        );
        MemoryDecision decision = MemoryDecision.create(
                UUID.randomUUID(), pending, ownerId, MemoryDecisionType.CONFIRM,
                null, "确认技能证据", NOW.plusSeconds(secondOffset)
        );
        return pending.applyDecision(decision);
    }
}