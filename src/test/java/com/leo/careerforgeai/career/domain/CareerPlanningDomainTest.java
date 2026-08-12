package com.leo.careerforgeai.career.domain;

import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapItem;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapStatus;
import com.leo.careerforgeai.career.domain.TrainingPlan.PlanStatus;
import com.leo.careerforgeai.career.domain.TrainingPlanItem.ItemStatus;
import com.leo.careerforgeai.career.domain.TrainingPlanItem.ResourceRef;
import com.leo.careerforgeai.career.domain.TrainingPlanItem.ResourceType;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证目标岗位、能力差距和训练计划的领域不变量与状态机
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class CareerPlanningDomainTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final UUID TARGET_ROLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID MEMORY_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ITEM_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-12T05:00:00Z");

    @Test
    void shouldCreateConfirmedTargetRoleWithFrozenInputVersion() {
        TargetRole targetRole = TargetRole.createConfirmed(
                TARGET_ROLE_ID,
                ACTOR_A,
                1,
                "jd-document-1",
                "a".repeat(64),
                "job-parser-v1",
                "job-prompt-v1",
                requirements(),
                NOW
        );

        assertThat(targetRole.ownerId()).isEqualTo(ACTOR_A);
        assertThat(targetRole.targetRoleVersion()).isEqualTo(1);
        assertThat(targetRole.requirementsSnapshot().jobTitle()).isEqualTo("AI Agent开发工程师");
    }

    @Test
    void shouldRejectTargetRoleWithoutTraceableSource() {
        assertThatThrownBy(() -> TargetRole.createConfirmed(
                TARGET_ROLE_ID,
                ACTOR_A,
                1,
                " ",
                "a".repeat(64),
                "job-parser-v1",
                "job-prompt-v1",
                requirements(),
                NOW
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceRef 不能为空");
    }

    @Test
    void shouldFreezeGapSnapshotAgainstTargetAndProfileVersions() {
        GapItem partialGap = new GapItem(
                GAP_ITEM_ID,
                "agentRequirements[0]",
                "掌握Agent状态管理与上下文管理",
                GapStatus.PARTIAL,
                List.of(MEMORY_ID),
                "有Agent开发经验，但缺少长期记忆状态管理证据"
        );

        SkillGapSnapshot snapshot = SkillGapSnapshot.create(
                SNAPSHOT_ID,
                ACTOR_A,
                TARGET_ROLE_ID,
                1,
                3,
                List.of(partialGap),
                NOW
        );

        assertThat(snapshot.targetRoleId()).isEqualTo(TARGET_ROLE_ID);
        assertThat(snapshot.targetRoleVersion()).isEqualTo(1);
        assertThat(snapshot.profileVersion()).isEqualTo(3);
        assertThat(snapshot.items()).containsExactly(partialGap);
    }

    @Test
    void shouldRejectMissingGapWithProfileEvidence() {
        assertThatThrownBy(() -> new GapItem(
                GAP_ITEM_ID,
                "ragRequirements[0]",
                "掌握混合检索与重排序",
                GapStatus.MISSING,
                List.of(MEMORY_ID),
                "当前画像没有足够证据"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MISSING差距不能包含证据Memory");
    }

    @Test
    void shouldRejectPlanItemWithoutGapOrFoundationGoal() {
        assertThatThrownBy(() -> TrainingPlanItem.createDraft(
                PLAN_ITEM_ID,
                1,
                "实现Memory确认流",
                "实现候选确认、拒绝和撤销状态转换",
                180,
                "状态机测试全部通过",
                "提交测试报告引用",
                List.of(),
                null,
                List.of(),
                NOW
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("计划项必须关联Gap或明确的基础准备目标");
    }

    @Test
    void shouldRejectUnknownKnowledgeChunkFormat() {
        assertThatThrownBy(() -> new ResourceRef(ResourceType.KNOWLEDGE_CHUNK, "unknown-chunk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("KNOWLEDGE_CHUNK的resourceId必须是小写SHA-256");
    }

    @Test
    void shouldRequireUserActivationAndEvidenceBeforeCompletingPlan() {
        TrainingPlan draft = draftPlan();

        assertThatThrownBy(() -> draft.activate(NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("只有PENDING_CONFIRMATION计划可以激活");

        TrainingPlan pending = draft.submitForConfirmation(NOW.plusSeconds(1));
        TrainingPlan active = pending.activate(NOW.plusSeconds(2));

        assertThat(pending.status()).isEqualTo(PlanStatus.PENDING_CONFIRMATION);
        assertThat(active.status()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(active.version()).isEqualTo(2);

        assertThatThrownBy(() -> active.complete(NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("所有计划项完成后才能完成训练计划");

        TrainingPlan started = active.startItem(PLAN_ITEM_ID, NOW.plusSeconds(3));
        TrainingPlan itemCompleted = started.completeItem(
                PLAN_ITEM_ID,
                List.of("project-evidence-1"),
                NOW.plusSeconds(4)
        );
        TrainingPlan completed = itemCompleted.complete(NOW.plusSeconds(5));

        assertThat(started.items().getFirst().status()).isEqualTo(ItemStatus.IN_PROGRESS);
        assertThat(itemCompleted.items().getFirst().status()).isEqualTo(ItemStatus.COMPLETED);
        assertThat(completed.status()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(completed.version()).isEqualTo(5);
    }

    @Test
    void shouldKeepRepeatedActivationIdempotent() {
        TrainingPlan active = draftPlan()
                .submitForConfirmation(NOW.plusSeconds(1))
                .activate(NOW.plusSeconds(2));

        TrainingPlan repeated = active.activate(NOW.plusSeconds(3));

        assertThat(repeated).isSameAs(active);
        assertThat(repeated.version()).isEqualTo(2);
    }

    private TrainingPlan draftPlan() {
        TrainingPlanItem item = TrainingPlanItem.createDraft(
                PLAN_ITEM_ID,
                1,
                "实现Memory确认流",
                "实现候选确认、拒绝和撤销状态转换",
                180,
                "状态机与owner隔离测试全部通过",
                "提交测试报告或代码提交引用",
                List.of(GAP_ITEM_ID),
                null,
                List.of(new ResourceRef(ResourceType.KNOWLEDGE_DOCUMENT, "memory-guide")),
                NOW
        );

        return TrainingPlan.createDraft(
                PLAN_ID,
                ACTOR_A,
                1,
                SNAPSHOT_ID,
                "AI Agent开发能力训练计划",
                List.of(item),
                NOW
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