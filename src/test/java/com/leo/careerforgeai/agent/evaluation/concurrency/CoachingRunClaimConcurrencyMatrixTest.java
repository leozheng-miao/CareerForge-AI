package com.leo.careerforgeai.agent.evaluation.concurrency;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimResult;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunRequestConflictException;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunRequestFingerprintService;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证相同requestId并发认领时的幂等重放和不同指纹冲突
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class CoachingRunClaimConcurrencyMatrixTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-25T08:00:00Z");

    @Test
    void shouldCreateOneRunAndReplayTheOtherForSameFingerprint() throws Exception {
        ConcurrentClaimFakeRepository repository = new ConcurrentClaimFakeRepository();
        CoachingRunClaimApplicationService service = service(repository);
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        List<CoachingRunClaimResult> results;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<CoachingRunClaimResult> first = executor.submit(() ->
                    claimAfterBarrier(service, startBarrier, "请解释Java并发"));
            Future<CoachingRunClaimResult> second = executor.submit(() ->
                    claimAfterBarrier(service, startBarrier, "请解释Java并发"));

            results = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
        }

        assertThat(results).extracting(result -> result.run().runId())
                .containsOnly(results.getFirst().run().runId());
        assertThat(results).extracting(CoachingRunClaimResult::replayed)
                .containsExactlyInAnyOrder(false, true);
        assertThat(repository.storedRunCount()).isEqualTo(1);
    }

    @Test
    void shouldCreateOneRunAndRejectTheOtherForDifferentFingerprint() throws Exception {
        ConcurrentClaimFakeRepository repository = new ConcurrentClaimFakeRepository();
        CoachingRunClaimApplicationService service = service(repository);
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        List<Object> outcomes;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Object> first = executor.submit(() ->
                    claimOutcome(service, startBarrier, "请解释Java并发"));
            Future<Object> second = executor.submit(() ->
                    claimOutcome(service, startBarrier, "请解释Redis限流"));

            outcomes = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
        }

        List<CoachingRunClaimResult> successes = outcomes.stream()
                .filter(CoachingRunClaimResult.class::isInstance)
                .map(CoachingRunClaimResult.class::cast)
                .toList();
        List<CoachingRunRequestConflictException> conflicts = outcomes.stream()
                .filter(CoachingRunRequestConflictException.class::isInstance)
                .map(CoachingRunRequestConflictException.class::cast)
                .toList();

        assertThat(successes).hasSize(1);
        assertThat(successes.getFirst().replayed()).isFalse();
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().existingRunId())
                .isEqualTo(successes.getFirst().run().runId());
        assertThat(repository.storedRunCount()).isEqualTo(1);
    }

    private CoachingRunClaimApplicationService service(
            CoachingRunRepository repository
    ) {
        CurrentActorProvider actorProvider = () -> OWNER;
        return new CoachingRunClaimApplicationService(
                actorProvider,
                repository,
                new CoachingRunRequestFingerprintService(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private CoachingRunClaimResult claimAfterBarrier(
            CoachingRunClaimApplicationService service,
            CyclicBarrier startBarrier,
            String message
    ) throws Exception {
        startBarrier.await(5, TimeUnit.SECONDS);
        return service.claim(SESSION_ID, REQUEST_ID, 4, message);
    }

    private Object claimOutcome(
            CoachingRunClaimApplicationService service,
            CyclicBarrier startBarrier,
            String message
    ) throws Exception {
        startBarrier.await(5, TimeUnit.SECONDS);
        try {
            return service.claim(SESSION_ID, REQUEST_ID, 4, message);
        } catch (CoachingRunRequestConflictException exception) {
            return exception;
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 使用ConcurrentHashMap模拟owner和requestId唯一约束的并发Run认领端口
     * @author: Miao Zheng
     * @date: 2026-08-25
     */
    private static final class ConcurrentClaimFakeRepository
            implements CoachingRunRepository {

        private final ConcurrentMap<String, CoachingRun> runsByRequest =
                new ConcurrentHashMap<>();

        @Override
        public CoachingRun claim(CoachingRun candidate) {
            String identity = requestIdentity(
                    candidate.ownerId(),
                    candidate.requestId()
            );
            CoachingRun existing = runsByRequest.putIfAbsent(identity, candidate);
            return existing == null ? candidate : existing;
        }

        @Override
        public Optional<CoachingRun> findByRunId(ActorId ownerId, UUID runId) {
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
                    runsByRequest.get(requestIdentity(ownerId, requestId))
            );
        }

        @Override
        public List<CoachingRun> findNonTerminalUpdatedBefore(
                Instant updatedBefore,
                int limit
        ) {
            throw new AssertionError("并发认领测试不应扫描非终态Run");
        }

        @Override
        public boolean updateIfVersionMatches(
                ActorId ownerId,
                CoachingRun updatedRun,
                long expectedVersion
        ) {
            throw new AssertionError("并发认领测试不应更新Run状态");
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