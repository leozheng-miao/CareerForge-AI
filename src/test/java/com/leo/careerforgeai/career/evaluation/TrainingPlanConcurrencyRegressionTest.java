package com.leo.careerforgeai.career.evaluation;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.training.TrainingPlanApplicationService;
import com.leo.careerforgeai.career.application.training.TrainingPlanVersionConflictException;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证阶段四训练计划激活、开始和取消操作的真实线程CAS竞争
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class TrainingPlanConcurrencyRegressionTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-25T14:00:00Z");

    @Test
    void shouldAllowOnlyOneConcurrentActivationCasWinner() throws Exception {
        ConcurrentPlanHarness harness =
                new ConcurrentPlanHarness(pendingPlan());
        TrainingPlanApplicationService service = harness.service();

        List<Object> outcomes = runConcurrently(
                () -> service.activate(PLAN_ID, 1),
                () -> service.activate(PLAN_ID, 1)
        );

        assertSingleWinnerAndConflict(outcomes);
        assertThat(harness.stored().status())
                .isEqualTo(TrainingPlan.PlanStatus.ACTIVE);
        assertThat(harness.stored().version()).isEqualTo(2);
        assertThat(harness.successfulUpdates()).isEqualTo(1);
    }

    @Test
    void shouldNotMergeConcurrentStartAndCancelMutations() throws Exception {
        TrainingPlan active = pendingPlan().activate(
                NOW.minusSeconds(1)
        );
        ConcurrentPlanHarness harness =
                new ConcurrentPlanHarness(active);
        TrainingPlanApplicationService service = harness.service();

        List<Object> outcomes = runConcurrently(
                () -> service.startItem(PLAN_ID, 2, ITEM_ID),
                () -> service.cancel(PLAN_ID, 2)
        );

        assertSingleWinnerAndConflict(outcomes);

        TrainingPlan stored = harness.stored();
        assertThat(stored.version()).isEqualTo(3);
        assertThat(harness.successfulUpdates()).isEqualTo(1);

        if (stored.status() == TrainingPlan.PlanStatus.CANCELLED) {
            assertThat(stored.items().getFirst().status())
                    .isEqualTo(
                            TrainingPlanItem.ItemStatus.NOT_STARTED
                    );
        } else {
            assertThat(stored.status())
                    .isEqualTo(TrainingPlan.PlanStatus.ACTIVE);
            assertThat(stored.items().getFirst().status())
                    .isEqualTo(
                            TrainingPlanItem.ItemStatus.IN_PROGRESS
                    );
        }
    }

    private List<Object> runConcurrently(
            Callable<TrainingPlan> firstOperation,
            Callable<TrainingPlan> secondOperation
    ) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Object> first = executor.submit(() ->
                    outcome(firstOperation));
            Future<Object> second = executor.submit(() ->
                    outcome(secondOperation));

            return List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
        }
    }

    private Object outcome(
            Callable<TrainingPlan> operation
    ) throws Exception {
        try {
            return operation.call();
        } catch (TrainingPlanVersionConflictException exception) {
            return exception;
        }
    }

    private void assertSingleWinnerAndConflict(List<Object> outcomes) {
        assertThat(outcomes.stream()
                .filter(TrainingPlan.class::isInstance)
                .toList()).hasSize(1);

        assertThat(outcomes.stream()
                .filter(
                        TrainingPlanVersionConflictException.class
                                ::isInstance
                )
                .toList()).hasSize(1);
    }

    private TrainingPlan pendingPlan() {
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
                OWNER,
                1,
                SNAPSHOT_ID,
                "AI Agent开发能力训练计划",
                List.of(item),
                createdAt
        ).submitForConfirmation(NOW.minusSeconds(5));
    }

    /**
     * @program: CareerForge-AI
     * @description: 使用同步读取点和AtomicReference模拟训练计划版本CAS端口
     * @author: Miao Zheng
     * @date: 2026-08-25
     */
    private static final class ConcurrentPlanHarness {

        private final CareerPlanningRepository repository =
                mock(CareerPlanningRepository.class);
        private final AtomicReference<TrainingPlan> stored;
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicInteger successfulUpdates =
                new AtomicInteger();
        private final CyclicBarrier readBarrier =
                new CyclicBarrier(2);

        private ConcurrentPlanHarness(TrainingPlan initialPlan) {
            stored = new AtomicReference<>(initialPlan);

            when(repository.findTrainingPlan(
                    any(ActorId.class),
                    any(UUID.class)
            )).thenAnswer(invocation -> {
                ActorId ownerId = invocation.getArgument(0);
                UUID planId = invocation.getArgument(1);
                TrainingPlan snapshot = stored.get();

                if (!snapshot.ownerId().equals(ownerId)
                        || !snapshot.planId().equals(planId)) {
                    return Optional.empty();
                }

                if (reads.incrementAndGet() <= 2) {
                    awaitReadBarrier();
                }
                return Optional.of(snapshot);
            });

            when(repository.updateTrainingPlanIfVersionMatches(
                    any(ActorId.class),
                    any(TrainingPlan.class),
                    anyLong()
            )).thenAnswer(invocation -> {
                ActorId ownerId = invocation.getArgument(0);
                TrainingPlan updatedPlan = invocation.getArgument(1);
                long expectedVersion = invocation.getArgument(2);
                TrainingPlan current = stored.get();

                if (!current.ownerId().equals(ownerId)
                        || !updatedPlan.ownerId().equals(ownerId)
                        || current.version() != expectedVersion
                        || updatedPlan.version()
                        != expectedVersion + 1) {
                    return false;
                }

                boolean updated = stored.compareAndSet(
                        current,
                        updatedPlan
                );
                if (updated) successfulUpdates.incrementAndGet();
                return updated;
            });
        }

        private TrainingPlanApplicationService service() {
            CurrentActorProvider actorProvider = () -> OWNER;
            return new TrainingPlanApplicationService(
                    actorProvider,
                    repository,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );
        }

        private TrainingPlan stored() {
            return stored.get();
        }

        private int successfulUpdates() {
            return successfulUpdates.get();
        }

        private void awaitReadBarrier() {
            try {
                readBarrier.await(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "训练计划并发读取同步失败",
                        exception
                );
            }
        }
    }
}