package com.leo.careerforgeai.agent.evaluation.concurrency;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimResult;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunRequestFingerprintService;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证Run终态CAS、并发幂等重试和跨owner隔离一致性
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class CoachingRunConsistencyMatrixTest {

    private static final ActorId OWNER_A = new ActorId("actor-a");
    private static final ActorId OWNER_B = new ActorId("actor-b");
    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SUCCESS_TURN_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID TIMEOUT_TURN_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");
    private static final String MESSAGE = "请分析我的Java并发能力";

    @Test
    void shouldAllowOnlyOneCompetingTerminalState() throws Exception {
        ConcurrentRunFakeRepository repository =
                new ConcurrentRunFakeRepository();
        CoachingRun running = runningRun(
                OWNER_A,
                RUN_ID,
                REQUEST_ID,
                fingerprint()
        );
        repository.claim(running);

        CoachingRun succeeded = running.succeed(SUCCESS_TURN_ID, NOW);
        CoachingRun timedOut = running.timeOut(
                TIMEOUT_TURN_ID,
                "MODEL_TIMEOUT",
                NOW
        );
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        List<Boolean> updates;
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> successFuture = executor.submit(() ->
                    updateAfterBarrier(
                            repository,
                            startBarrier,
                            succeeded
                    ));
            Future<Boolean> timeoutFuture = executor.submit(() ->
                    updateAfterBarrier(
                            repository,
                            startBarrier,
                            timedOut
                    ));

            updates = List.of(
                    successFuture.get(5, TimeUnit.SECONDS),
                    timeoutFuture.get(5, TimeUnit.SECONDS)
            );
        }

        assertThat(updates).containsExactlyInAnyOrder(true, false);

        CoachingRun persisted = repository
                .findByRunId(OWNER_A, RUN_ID)
                .orElseThrow();

        assertThat(persisted.version()).isEqualTo(3);
        assertThat(persisted.status())
                .isIn(
                        CoachingRunStatus.SUCCEEDED,
                        CoachingRunStatus.TIMED_OUT
                );

        if (persisted.status() == CoachingRunStatus.SUCCEEDED) {
            assertThat(persisted.assistantTurnId())
                    .isEqualTo(SUCCESS_TURN_ID);
            assertThat(persisted.failureCode()).isNull();
        } else {
            assertThat(persisted.assistantTurnId())
                    .isEqualTo(TIMEOUT_TURN_ID);
            assertThat(persisted.failureCode())
                    .isEqualTo("MODEL_TIMEOUT");
        }
    }

    @Test
    void shouldReplayOriginalRunWhileItCompletesConcurrently() throws Exception {
        ConcurrentRunFakeRepository repository =
                new ConcurrentRunFakeRepository();
        CoachingRun running = runningRun(
                OWNER_A,
                RUN_ID,
                REQUEST_ID,
                fingerprint()
        );
        repository.claim(running);

        CoachingRun succeeded = running.succeed(SUCCESS_TURN_ID, NOW);
        CoachingRunClaimApplicationService claimService =
                claimService(OWNER_A, repository);
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        boolean completed;
        CoachingRunClaimResult replayed;

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> completionFuture = executor.submit(() ->
                    updateAfterBarrier(
                            repository,
                            startBarrier,
                            succeeded
                    ));
            Future<CoachingRunClaimResult> retryFuture =
                    executor.submit(() -> {
                        startBarrier.await(5, TimeUnit.SECONDS);
                        return claimService.claim(
                                SESSION_ID,
                                REQUEST_ID,
                                4,
                                MESSAGE
                        );
                    });

            completed = completionFuture.get(5, TimeUnit.SECONDS);
            replayed = retryFuture.get(5, TimeUnit.SECONDS);
        }

        assertThat(completed).isTrue();
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.run().runId()).isEqualTo(RUN_ID);
        assertThat(replayed.run().status())
                .isIn(
                        CoachingRunStatus.RUNNING,
                        CoachingRunStatus.SUCCEEDED
                );
        assertThat(repository.storedRunCount()).isEqualTo(1);

        CoachingRun persisted = repository
                .findByRunId(OWNER_A, RUN_ID)
                .orElseThrow();

        assertThat(persisted.status())
                .isEqualTo(CoachingRunStatus.SUCCEEDED);
        assertThat(persisted.assistantTurnId())
                .isEqualTo(SUCCESS_TURN_ID);
    }

    @Test
    void shouldIsolateSameRequestIdBetweenOwners() {
        ConcurrentRunFakeRepository repository =
                new ConcurrentRunFakeRepository();
        CoachingRunClaimApplicationService ownerAService =
                claimService(OWNER_A, repository);
        CoachingRunClaimApplicationService ownerBService =
                claimService(OWNER_B, repository);

        CoachingRunClaimResult ownerAResult = ownerAService.claim(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        );
        CoachingRunClaimResult ownerBResult = ownerBService.claim(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        );

        assertThat(ownerAResult.replayed()).isFalse();
        assertThat(ownerBResult.replayed()).isFalse();
        assertThat(ownerAResult.run().runId())
                .isNotEqualTo(ownerBResult.run().runId());
        assertThat(repository.storedRunCount()).isEqualTo(2);

        assertThat(repository.findByRunId(
                OWNER_B,
                ownerAResult.run().runId()
        )).isEmpty();

        assertThat(repository.findByRunId(
                OWNER_A,
                ownerBResult.run().runId()
        )).isEmpty();

        assertThat(repository.findByRequestId(OWNER_A, REQUEST_ID))
                .contains(ownerAResult.run());
        assertThat(repository.findByRequestId(OWNER_B, REQUEST_ID))
                .contains(ownerBResult.run());
    }

    private boolean updateAfterBarrier(
            CoachingRunRepository repository,
            CyclicBarrier startBarrier,
            CoachingRun updatedRun
    ) throws Exception {
        startBarrier.await(5, TimeUnit.SECONDS);
        return repository.updateIfVersionMatches(
                OWNER_A,
                updatedRun,
                2
        );
    }

    private CoachingRunClaimApplicationService claimService(
            ActorId ownerId,
            CoachingRunRepository repository
    ) {
        CurrentActorProvider actorProvider = () -> ownerId;
        return new CoachingRunClaimApplicationService(
                actorProvider,
                repository,
                new CoachingRunRequestFingerprintService(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private CoachingRun runningRun(
            ActorId ownerId,
            UUID runId,
            UUID requestId,
            String requestFingerprint
    ) {
        return CoachingRun.receive(
                runId,
                ownerId,
                SESSION_ID,
                requestId,
                requestFingerprint,
                4,
                NOW.minusSeconds(30)
        ).accept(
                USER_TURN_ID,
                NOW.minusSeconds(20)
        ).start(
                NOW.minusSeconds(10)
        );
    }

    private String fingerprint() {
        return new CoachingRunRequestFingerprintService()
                .fingerprint(SESSION_ID, 4, MESSAGE);
    }

    /**
     * @program: CareerForge-AI
     * @description: 原子模拟Run请求唯一约束、owner查询和聚合版本CAS的并发Fake端口
     * @author: Miao Zheng
     * @date: 2026-08-25
     */
    private static final class ConcurrentRunFakeRepository
            implements CoachingRunRepository {

        private final ConcurrentMap<String, CoachingRun> runsByRequest =
                new ConcurrentHashMap<>();

        @Override
        public CoachingRun claim(CoachingRun candidate) {
            String identity = requestIdentity(
                    candidate.ownerId(),
                    candidate.requestId()
            );
            return runsByRequest.computeIfAbsent(
                    identity,
                    ignored -> candidate
            );
        }

        @Override
        public Optional<CoachingRun> findByRunId(
                ActorId ownerId,
                UUID runId
        ) {
            return runsByRequest.values().stream()
                    .filter(run -> run.ownerId().equals(ownerId))
                    .filter(run -> run.runId().equals(runId))
                    .findFirst();
        }

        @Override
        public Optional<CoachingRun> findByRequestId(
                ActorId ownerId,
                UUID requestId
        ) {
            return Optional.ofNullable(
                    runsByRequest.get(
                            requestIdentity(ownerId, requestId)
                    )
            );
        }

        @Override
        public List<CoachingRun> findNonTerminalUpdatedBefore(
                Instant updatedBefore,
                int limit
        ) {
            return runsByRequest.values().stream()
                    .filter(run -> !run.isTerminal())
                    .filter(run -> run.updatedAt().isBefore(updatedBefore))
                    .sorted(
                            java.util.Comparator.comparing(
                                    CoachingRun::updatedAt
                            )
                    )
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean updateIfVersionMatches(
                ActorId ownerId,
                CoachingRun updatedRun,
                long expectedVersion
        ) {
            if (!ownerId.equals(updatedRun.ownerId())) {
                throw new IllegalArgumentException(
                        "ownerId与Run归属不一致"
                );
            }
            if (updatedRun.version() != expectedVersion + 1) {
                throw new IllegalArgumentException(
                        "更新后的Run版本非法"
                );
            }

            String identity = requestIdentity(
                    ownerId,
                    updatedRun.requestId()
            );
            AtomicBoolean updated = new AtomicBoolean();

            runsByRequest.computeIfPresent(identity, (ignored, current) -> {
                if (!current.runId().equals(updatedRun.runId())) {
                    return current;
                }
                if (current.version() != expectedVersion) {
                    return current;
                }

                updated.set(true);
                return updatedRun;
            });

            return updated.get();
        }

        @Override
        public List<CoachingRun> findPage(
                ActorId ownerId,
                UUID sessionId,
                CoachingRunStatus status,
                Instant beforeCreatedAt,
                UUID beforeRunId,
                int limit
        ) {
            throw new AssertionError("当前并发测试不应执行Run分页查询");
        }

        private int storedRunCount() {
            return runsByRequest.size();
        }

        private static String requestIdentity(
                ActorId ownerId,
                UUID requestId
        ) {
            return ownerId.value() + "\u0000" + requestId;
        }
    }
}