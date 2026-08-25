package com.leo.careerforgeai.agent.evaluation.concurrency;

import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionVersionConflictException;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
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
 * @description: 验证相同Session版本的不同消息并发写入时只有一个USER Turn成功
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class CoachingSessionConcurrentWriteMatrixTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-25T09:00:00Z");

    @Test
    void shouldAcceptOnlyOneMessageForSameSessionVersion() throws Exception {
        ConcurrentConversationFakeRepository repository =
                new ConcurrentConversationFakeRepository();
        CoachingSession initialSession = CoachingSession.create(
                SESSION_ID,
                OWNER,
                "并发会话",
                NOW
        );
        repository.insertSession(initialSession);

        CurrentActorProvider actorProvider = () -> OWNER;
        CoachingSessionApplicationService service =
                new CoachingSessionApplicationService(
                        actorProvider,
                        repository,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        List<Object> outcomes;
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Object> first = executor.submit(() ->
                    recordOutcome(
                            service,
                            startBarrier,
                            "第一个并发问题"
                    ));
            Future<Object> second = executor.submit(() ->
                    recordOutcome(
                            service,
                            startBarrier,
                            "第二个并发问题"
                    ));

            outcomes = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
        }

        List<ConversationTurn> successfulTurns = outcomes.stream()
                .filter(ConversationTurn.class::isInstance)
                .map(ConversationTurn.class::cast)
                .toList();
        List<CoachingSessionVersionConflictException> conflicts =
                outcomes.stream()
                        .filter(
                                CoachingSessionVersionConflictException.class
                                        ::isInstance
                        )
                        .map(
                                CoachingSessionVersionConflictException.class
                                        ::cast
                        )
                        .toList();

        assertThat(successfulTurns).hasSize(1);
        assertThat(successfulTurns.getFirst().turnSequence()).isEqualTo(1);
        assertThat(successfulTurns.getFirst().content())
                .isIn("第一个并发问题", "第二个并发问题");

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().getMessage())
                .isEqualTo("Session并发更新冲突");

        CoachingSession persistedSession = repository
                .findSession(OWNER, SESSION_ID)
                .orElseThrow();

        assertThat(persistedSession.version()).isEqualTo(1);
        assertThat(persistedSession.nextTurnSequence()).isEqualTo(2);

        List<ConversationTurn> persistedTurns =
                repository.findRecentTurns(OWNER, SESSION_ID, 20);

        assertThat(persistedTurns).hasSize(1);
        assertThat(persistedTurns.getFirst().turnId())
                .isEqualTo(successfulTurns.getFirst().turnId());
    }

    private Object recordOutcome(
            CoachingSessionApplicationService service,
            CyclicBarrier startBarrier,
            String message
    ) throws Exception {
        startBarrier.await(5, TimeUnit.SECONDS);
        try {
            return service.recordUserTurn(SESSION_ID, 0, message);
        } catch (CoachingSessionVersionConflictException exception) {
            return exception;
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 原子模拟Session乐观锁和Conversation Turn持久化的并发Fake端口
     * @author: Miao Zheng
     * @date: 2026-08-25
     */
    private static final class ConcurrentConversationFakeRepository
            implements CoachingConversationRepository {

        private final ConcurrentMap<String, CoachingSession> sessions =
                new ConcurrentHashMap<>();
        private final ConcurrentMap<UUID, ConversationTurn> turns =
                new ConcurrentHashMap<>();

        @Override
        public void insertSession(CoachingSession session) {
            String key = sessionIdentity(
                    session.ownerId(),
                    session.sessionId()
            );
            if (sessions.putIfAbsent(key, session) != null) {
                throw new IllegalStateException("Session已经存在");
            }
        }

        @Override
        public Optional<CoachingSession> findSession(
                ActorId ownerId,
                UUID sessionId
        ) {
            return Optional.ofNullable(
                    sessions.get(sessionIdentity(ownerId, sessionId))
            );
        }

        @Override
        public boolean updateSessionIfVersionMatches(
                ActorId ownerId,
                CoachingSession updatedSession,
                long expectedVersion
        ) {
            String key = sessionIdentity(
                    ownerId,
                    updatedSession.sessionId()
            );
            AtomicBoolean updated = new AtomicBoolean();

            sessions.computeIfPresent(key, (ignored, current) -> {
                if (current.version() != expectedVersion) return current;
                if (updatedSession.version() != expectedVersion + 1) {
                    throw new IllegalArgumentException(
                            "更新后的Session版本非法"
                    );
                }
                updated.set(true);
                return updatedSession;
            });

            return updated.get();
        }

        @Override
        public void insertTurn(ConversationTurn turn) {
            if (findSession(
                    turn.ownerId(),
                    turn.sessionId()
            ).isEmpty()) {
                throw new IllegalStateException("Turn所属Session不存在");
            }

            boolean duplicateSequence = turns.values().stream()
                    .anyMatch(existing ->
                            existing.ownerId().equals(turn.ownerId())
                                    && existing.sessionId()
                                    .equals(turn.sessionId())
                                    && existing.turnSequence()
                                    == turn.turnSequence());

            if (duplicateSequence) {
                throw new IllegalStateException("Turn序号已经存在");
            }
            if (turns.putIfAbsent(turn.turnId(), turn) != null) {
                throw new IllegalStateException("Turn已经存在");
            }
        }

        @Override
        public Optional<ConversationTurn> findTurn(
                ActorId ownerId,
                UUID turnId
        ) {
            return Optional.ofNullable(turns.get(turnId))
                    .filter(turn -> turn.ownerId().equals(ownerId));
        }

        @Override
        public List<ConversationTurn> findRecentTurns(
                ActorId ownerId,
                UUID sessionId,
                int limit
        ) {
            List<ConversationTurn> orderedTurns = turns.values().stream()
                    .filter(turn -> turn.ownerId().equals(ownerId))
                    .filter(turn -> turn.sessionId().equals(sessionId))
                    .sorted(
                            Comparator.comparingLong(
                                    ConversationTurn::turnSequence
                            )
                    )
                    .toList();

            int fromIndex = Math.max(0, orderedTurns.size() - limit);
            return List.copyOf(
                    orderedTurns.subList(
                            fromIndex,
                            orderedTurns.size()
                    )
            );
        }

        private static String sessionIdentity(
                ActorId ownerId,
                UUID sessionId
        ) {
            return ownerId.value() + "\u0000" + sessionId;
        }
    }
}