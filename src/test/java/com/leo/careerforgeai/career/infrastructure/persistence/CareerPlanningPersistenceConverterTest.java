package com.leo.careerforgeai.career.infrastructure.persistence;

import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.career.infrastructure.persistence.converter.CareerPlanningPersistenceConverter;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanItemEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证求职规划领域对象经过Entity和JSON转换后不丢失字段
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class CareerPlanningPersistenceConverterTest {

    private static final ActorId ACTOR = new ActorId("actor-a");
    private static final UUID TARGET_ROLE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID MEMORY_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ITEM_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-12T07:00:00Z");

    private final CareerPlanningPersistenceConverter converter =
            new CareerPlanningPersistenceConverter(
                    JsonMapper.builder().build()
            );

    @Test
    void shouldRoundTripTargetRole() {
        TargetRole targetRole = TargetRole.createConfirmed(
                TARGET_ROLE_ID,
                ACTOR,
                1,
                "jd-document-1",
                "a".repeat(64),
                "job-parser-v1",
                "job-prompt-v1",
                requirements(),
                NOW
        );

        TargetRole restored = converter.toDomain(
                converter.toEntity(targetRole)
        );

        assertThat(restored).isEqualTo(targetRole);
    }

    @Test
    void shouldRoundTripSkillGapSnapshot() {
        SkillGapSnapshot snapshot = gapSnapshot();

        SkillGapSnapshot restored = converter.toDomain(
                converter.toEntity(snapshot)
        );

        assertThat(restored).isEqualTo(snapshot);
        assertThat(restored.algorithmVersion())
                .isEqualTo("deterministic-skill-gap-v1");
    }

    @Test
    void shouldRoundTripCompleteTrainingPlanAggregate() {
        TrainingPlan activePlan = activePlan();

        TrainingPlanEntity planEntity =
                converter.toEntity(activePlan);

        List<TrainingPlanItemEntity> itemEntities =
                activePlan.items().stream()
                        .map(item -> converter.toEntity(
                                activePlan.ownerId(),
                                activePlan.planId(),
                                item
                        ))
                        .toList();

        TrainingPlan restored = converter.toDomain(
                planEntity,
                itemEntities
        );

        assertThat(restored).isEqualTo(activePlan);
    }

    private SkillGapSnapshot gapSnapshot() {
        SkillGapSnapshot.GapItem gapItem =
                new SkillGapSnapshot.GapItem(
                        GAP_ITEM_ID,
                        "agentRequirements[0]",
                        "掌握Agent状态管理与上下文管理",
                        SkillGapSnapshot.GapStatus.PARTIAL,
                        List.of(MEMORY_ID),
                        "已有Agent开发证据，但缺少长期记忆实践"
                );

        return SkillGapSnapshot.create(
                SNAPSHOT_ID,
                ACTOR,
                TARGET_ROLE_ID,
                1,
                3,
                "deterministic-skill-gap-v1",
                List.of(gapItem),
                NOW
        );
    }

    private TrainingPlan activePlan() {
        TrainingPlanItem item =
                TrainingPlanItem.createDraft(
                        PLAN_ITEM_ID,
                        1,
                        "实现Memory确认流",
                        "实现候选确认、拒绝和撤销状态转换",
                        180,
                        "状态机测试全部通过",
                        "提交测试报告引用",
                        List.of(GAP_ITEM_ID),
                        null,
                        List.of(
                                new TrainingPlanItem.ResourceRef(
                                        TrainingPlanItem.ResourceType.KNOWLEDGE_CHUNK,
                                        "b".repeat(64)
                                )
                        ),
                        NOW
                );

        return TrainingPlan.createDraft(
                PLAN_ID,
                ACTOR,
                1,
                SNAPSHOT_ID,
                "AI Agent开发能力训练计划",
                List.of(item),
                NOW
        ).submitForConfirmation(
                NOW.plusSeconds(1)
        ).activate(
                NOW.plusSeconds(2)
        );
    }

    private JobRequirements requirements() {
        return new JobRequirements(
                "AI Agent开发工程师",
                List.of("Java"),
                List.of("Spring Boot"),
                List.of("Agent状态管理"),
                List.of("RAG"),
                List.of("自动化测试"),
                List.of(),
                List.of("开发AI Agent应用"),
                List.of("Memory状态机")
        );
    }
}