package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.training.TrainingPlanApplicationService;
import com.leo.careerforgeai.career.application.training.TrainingPlanVersionConflictException;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.testsupport.MutableCurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 使用固定时钟和内存状态验证训练计划应用服务的owner隔离、幂等和乐观锁
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class TrainingPlanApplicationServiceTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final ActorId ACTOR_B = new ActorId("actor-b");
    private static final UUID SNAPSHOT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-12T06:00:00Z");

    private MutableCurrentActorProvider actorProvider;
    private CareerPlanningRepository repository;
    private AtomicReference<TrainingPlan> storedPlan;
    private AtomicInteger updateCount;
    private TrainingPlanApplicationService service;

    @BeforeEach
    void setUp() {
        actorProvider = new MutableCurrentActorProvider(ACTOR_A);
        repository = mock(CareerPlanningRepository.class);
        storedPlan = new AtomicReference<>();
        updateCount = new AtomicInteger();

        when(repository.findTrainingPlan(any(ActorId.class), any(UUID.class)))
                .thenAnswer(invocation -> {
                    ActorId actorId = invocation.getArgument(0);
                    UUID planId = invocation.getArgument(1);
                    TrainingPlan currentPlan = storedPlan.get();

                    if (currentPlan == null
                            || !currentPlan.ownerId().equals(actorId)
                            || !currentPlan.planId().equals(planId)) {
                        return Optional.empty();
                    }

                    return Optional.of(currentPlan);
                });

        when(repository.updateTrainingPlanIfVersionMatches(
                any(ActorId.class),
                any(TrainingPlan.class),
                anyLong()
        )).thenAnswer(invocation -> {
            ActorId actorId = invocation.getArgument(0);
            TrainingPlan updatedPlan = invocation.getArgument(1);
            long expectedVersion = invocation.getArgument(2);
            TrainingPlan currentPlan = storedPlan.get();

            if (currentPlan == null
                    || !currentPlan.ownerId().equals(actorId)
                    || !updatedPlan.ownerId().equals(actorId)
                    || !currentPlan.planId().equals(updatedPlan.planId())
                    || currentPlan.version() != expectedVersion) {
                return false;
            }

            storedPlan.set(updatedPlan);
            updateCount.incrementAndGet();
            return true;
        });

        service = new TrainingPlanApplicationService(
                actorProvider,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldActivateUpdateItemAndCompleteOwnedPlan() {
        storedPlan.set(pendingPlan(ACTOR_A));

        TrainingPlan active = service.activate(PLAN_ID, 1);
        TrainingPlan started = service.startItem(PLAN_ID, 2, ITEM_ID);
        TrainingPlan itemCompleted = service.completeItem(
                PLAN_ID,
                3,
                ITEM_ID,
                List.of("github:commit/abc123")
        );
        TrainingPlan completed = service.complete(PLAN_ID, 4);

        assertThat(active.status()).isEqualTo(TrainingPlan.PlanStatus.ACTIVE);
        assertThat(started.items().getFirst().status())
                .isEqualTo(TrainingPlanItem.ItemStatus.IN_PROGRESS);
        assertThat(itemCompleted.items().getFirst().status())
                .isEqualTo(TrainingPlanItem.ItemStatus.COMPLETED);
        assertThat(completed.status()).isEqualTo(TrainingPlan.PlanStatus.COMPLETED);
        assertThat(completed.version()).isEqualTo(5);
        assertThat(storedPlan.get()).isEqualTo(completed);
        assertThat(updateCount).hasValue(4);
    }

    @Test
    void shouldHidePlanFromAnotherActor() {
        TrainingPlan original = pendingPlan(ACTOR_A);
        storedPlan.set(original);
        actorProvider.switchTo(ACTOR_B);

        assertThatThrownBy(() -> service.get(PLAN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("训练计划不存在或不属于当前用户");

        assertThatThrownBy(() -> service.activate(PLAN_ID, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("训练计划不存在或不属于当前用户");

        assertThat(storedPlan.get()).isEqualTo(original);
        assertThat(updateCount).hasValue(0);
    }

    @Test
    void shouldKeepRepeatedActivationIdempotentWithoutSecondUpdate() {
        storedPlan.set(pendingPlan(ACTOR_A));

        TrainingPlan firstResult = service.activate(PLAN_ID, 1);
        TrainingPlan repeatedResult = service.activate(PLAN_ID, 1);

        assertThat(repeatedResult).isSameAs(firstResult);
        assertThat(repeatedResult.version()).isEqualTo(2);
        assertThat(updateCount).hasValue(1);
    }

    @Test
    void shouldRejectStaleVersionBeforePersistingNewProgress() {
        storedPlan.set(pendingPlan(ACTOR_A));
        TrainingPlan active = service.activate(PLAN_ID, 1);

        assertThatThrownBy(() -> service.startItem(PLAN_ID, 1, ITEM_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("训练计划版本已经过期");

        assertThat(storedPlan.get()).isEqualTo(active);
        assertThat(storedPlan.get().items().getFirst().status())
                .isEqualTo(TrainingPlanItem.ItemStatus.NOT_STARTED);
        assertThat(updateCount).hasValue(1);
    }

    @Test
    void shouldRejectStaleVersionWhenConfirmingPendingPlan() {
        TrainingPlan original = pendingPlan(ACTOR_A);
        storedPlan.set(original);

        assertThatThrownBy(() -> service.activate(PLAN_ID, 0))
                .isInstanceOf(TrainingPlanVersionConflictException.class)
                .hasMessage("训练计划版本已经过期");

        assertThat(storedPlan.get()).isSameAs(original);
        assertThat(storedPlan.get().status())
                .isEqualTo(TrainingPlan.PlanStatus.PENDING_CONFIRMATION);
        assertThat(updateCount).hasValue(0);
    }

    @Test
    void shouldRejectCompletingItemBeforeStart() {
        storedPlan.set(pendingPlan(ACTOR_A));
        TrainingPlan active = service.activate(PLAN_ID, 1);

        assertThatThrownBy(() ->
                service.completeItem(
                        PLAN_ID,
                        2,
                        ITEM_ID,
                        List.of("github:commit/abc123")
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("只有IN_PROGRESS计划项可以完成");

        assertThat(storedPlan.get()).isSameAs(active);
        assertThat(updateCount).hasValue(1);
    }

    @Test
    void shouldRejectUncontrolledEvidenceWithoutUpdatingPlan() {
        storedPlan.set(pendingPlan(ACTOR_A));
        service.activate(PLAN_ID, 1);
        TrainingPlan started = service.startItem(PLAN_ID, 2, ITEM_ID);

        assertThatThrownBy(() ->
                service.completeItem(
                        PLAN_ID,
                        3,
                        ITEM_ID,
                        List.of("开始第一次item测试")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("完成证据引用格式不受支持");

        assertThat(storedPlan.get()).isSameAs(started);
        assertThat(storedPlan.get().version()).isEqualTo(3);
        assertThat(storedPlan.get().items().getFirst().status())
                .isEqualTo(TrainingPlanItem.ItemStatus.IN_PROGRESS);
        assertThat(updateCount).hasValue(2);
    }

    @Test
    void shouldCancelOwnedPlanIdempotentlyWithoutSecondUpdate() {
        storedPlan.set(pendingPlan(ACTOR_A));

        TrainingPlan cancelled = service.cancel(PLAN_ID, 1);
        TrainingPlan repeated = service.cancel(PLAN_ID, 1);

        assertThat(cancelled.status()).isEqualTo(TrainingPlan.PlanStatus.CANCELLED);
        assertThat(cancelled.version()).isEqualTo(2);
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
        assertThat(repeated).isSameAs(cancelled);
        assertThat(storedPlan.get()).isSameAs(cancelled);
        assertThat(updateCount).hasValue(1);
    }

    private TrainingPlan pendingPlan(ActorId ownerId) {
        Instant createdAt = NOW.minusSeconds(10);

        TrainingPlanItem item = TrainingPlanItem.createDraft(
                ITEM_ID,
                1,
                "实现Memory确认流",
                "实现候选确认、拒绝和撤销状态转换",
                180,
                "状态机与owner隔离测试全部通过",
                "提交测试报告或代码提交引用",
                List.of(GAP_ITEM_ID),
                null,
                List.of(),
                createdAt
        );

        return TrainingPlan.createDraft(
                PLAN_ID,
                ownerId,
                1,
                SNAPSHOT_ID,
                "AI Agent开发能力训练计划",
                List.of(item),
                createdAt
        ).submitForConfirmation(NOW.minusSeconds(5));
    }
}