package com.leo.careerforgeai.career.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.career.infrastructure.persistence.adapter.MyBatisPlusCareerPlanningAdapter;
import com.leo.careerforgeai.career.infrastructure.persistence.converter.CareerPlanningPersistenceConverter;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.entity.TrainingPlanItemEntity;
import com.leo.careerforgeai.career.infrastructure.persistence.mapper.SkillGapSnapshotMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.mapper.TargetRoleMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.mapper.TrainingPlanItemMapper;
import com.leo.careerforgeai.career.infrastructure.persistence.mapper.TrainingPlanMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 使用Mock Mapper验证求职规划Adapter的owner条件、乐观锁参数和失败边界
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class MyBatisPlusCareerPlanningAdapterTest {

    private static final ActorId ACTOR_A =
            new ActorId("actor-a");
    private static final ActorId ACTOR_B =
            new ActorId("actor-b");

    private static final UUID SNAPSHOT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-12T08:00:00Z");

    private TargetRoleMapper targetRoleMapper;
    private SkillGapSnapshotMapper gapSnapshotMapper;
    private TrainingPlanMapper trainingPlanMapper;
    private TrainingPlanItemMapper trainingPlanItemMapper;
    private CareerPlanningPersistenceConverter converter;
    private MyBatisPlusCareerPlanningAdapter adapter;

    @BeforeAll
    static void initializeMyBatisPlusMetadata() {
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                "career-planning-adapter-unit-test"
        );

        TableInfoHelper.initTableInfo(
                builderAssistant,
                TrainingPlanEntity.class
        );
    }

    @BeforeEach
    void setUp() {
        targetRoleMapper = mock(TargetRoleMapper.class);
        gapSnapshotMapper = mock(SkillGapSnapshotMapper.class);
        trainingPlanMapper = mock(TrainingPlanMapper.class);
        trainingPlanItemMapper = mock(TrainingPlanItemMapper.class);
        converter = new CareerPlanningPersistenceConverter(
                JsonMapper.builder().build()
        );

        adapter = new MyBatisPlusCareerPlanningAdapter(
                targetRoleMapper,
                gapSnapshotMapper,
                trainingPlanMapper,
                trainingPlanItemMapper,
                converter
        );
    }

    @Test
    void shouldIncludeOwnerAndPlanIdWhenReadingPlan() {
        when(trainingPlanMapper.selectOne(any()))
                .thenReturn(null);

        Optional<TrainingPlan> result =
                adapter.findTrainingPlan(ACTOR_A, PLAN_ID);

        assertThat(result).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<TrainingPlanEntity>>
                queryCaptor = ArgumentCaptor.forClass(
                        LambdaQueryWrapper.class
                );

        verify(trainingPlanMapper)
                .selectOne(queryCaptor.capture());

        LambdaQueryWrapper<TrainingPlanEntity> query =
                queryCaptor.getValue();

        assertThat(query.getSqlSegment())
                .contains("owner_id")
                .contains("plan_id");

        assertThat(query.getParamNameValuePairs().values())
                .contains(ACTOR_A.value(), PLAN_ID.toString());
    }

    @Test
    void shouldRejectOwnerMismatchBeforeAccessingDatabase() {
        TrainingPlan updatedPlan =
                activePlan(ACTOR_A).startItem(
                        ITEM_ID,
                        NOW.plusSeconds(3)
                );

        assertThatThrownBy(() ->
                adapter.updateTrainingPlanIfVersionMatches(
                        ACTOR_B,
                        updatedPlan,
                        2
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ownerId与TrainingPlan归属不一致");

        verifyNoInteractions(
                trainingPlanMapper,
                trainingPlanItemMapper
        );
    }

    @Test
    void shouldPassOwnerAndVersionsToPlanAndItemUpdates() {
        TrainingPlan currentPlan = activePlan(ACTOR_A);
        TrainingPlan updatedPlan = currentPlan.startItem(
                ITEM_ID,
                NOW.plusSeconds(3)
        );

        configureCurrentPlan(currentPlan);

        when(trainingPlanMapper.updateStateIfVersionMatches(
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                anyLong()
        )).thenReturn(1);

        when(trainingPlanItemMapper.updateProgressIfVersionMatches(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                any(),
                anyLong()
        )).thenReturn(1);

        boolean updated =
                adapter.updateTrainingPlanIfVersionMatches(
                        ACTOR_A,
                        updatedPlan,
                        2
                );

        assertThat(updated).isTrue();

        verify(trainingPlanMapper)
                .updateStateIfVersionMatches(
                        eq(PLAN_ID.toString()),
                        eq(ACTOR_A.value()),
                        eq(TrainingPlan.PlanStatus.ACTIVE.name()),
                        eq(3L),
                        eq(updatedPlan.updatedAt()),
                        eq(updatedPlan.activatedAt()),
                        eq(updatedPlan.completedAt()),
                        eq(updatedPlan.cancelledAt()),
                        eq(2L)
                );

        verify(trainingPlanItemMapper)
                .updateProgressIfVersionMatches(
                        eq(ITEM_ID.toString()),
                        eq(PLAN_ID.toString()),
                        eq(ACTOR_A.value()),
                        eq(TrainingPlanItem.ItemStatus.IN_PROGRESS.name()),
                        eq("[]"),
                        eq(1L),
                        eq(updatedPlan.items().getFirst().updatedAt()),
                        eq(0L)
                );
    }

    @Test
    void shouldThrowWhenItemUpdateFailsAfterPlanUpdate() {
        TrainingPlan currentPlan = activePlan(ACTOR_A);
        TrainingPlan updatedPlan = currentPlan.startItem(
                ITEM_ID,
                NOW.plusSeconds(3)
        );

        configureCurrentPlan(currentPlan);

        when(trainingPlanMapper.updateStateIfVersionMatches(
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                any(),
                any(),
                any(),
                any(),
                anyLong()
        )).thenReturn(1);

        when(trainingPlanItemMapper.updateProgressIfVersionMatches(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                any(),
                anyLong()
        )).thenReturn(0);

        assertThatThrownBy(() ->
                adapter.updateTrainingPlanIfVersionMatches(
                        ACTOR_A,
                        updatedPlan,
                        2
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "TrainingPlanItem并发更新冲突，事务必须回滚"
                );
    }

    private void configureCurrentPlan(TrainingPlan currentPlan) {
        TrainingPlanEntity planEntity =
                converter.toEntity(currentPlan);

        List<TrainingPlanItemEntity> itemEntities =
                currentPlan.items().stream()
                        .map(item -> converter.toEntity(
                                currentPlan.ownerId(),
                                currentPlan.planId(),
                                item
                        ))
                        .toList();

        when(trainingPlanMapper.selectOne(any()))
                .thenReturn(planEntity);

        when(trainingPlanItemMapper.selectList(any()))
                .thenReturn(itemEntities);
    }

    private TrainingPlan activePlan(ActorId ownerId) {
        TrainingPlanItem item =
                TrainingPlanItem.createDraft(
                        ITEM_ID,
                        1,
                        "实现Memory确认流",
                        "实现候选确认、拒绝和撤销状态转换",
                        180,
                        "状态机测试全部通过",
                        "提交测试报告引用",
                        List.of(GAP_ITEM_ID),
                        null,
                        List.of(),
                        NOW
                );

        return TrainingPlan.createDraft(
                PLAN_ID,
                ownerId,
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
}