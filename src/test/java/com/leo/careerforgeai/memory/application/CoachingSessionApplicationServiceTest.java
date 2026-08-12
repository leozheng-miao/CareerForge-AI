package com.leo.careerforgeai.memory.application;

import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSessionStatus;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnRole;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.testsupport.MutableCurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 使用内存Repository验证会话生命周期、Turn顺序、问答配对和owner隔离
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class CoachingSessionApplicationServiceTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final ActorId ACTOR_B = new ActorId("actor-b");
    private static final Instant NOW = Instant.parse("2026-08-12T03:00:00Z");

    private MutableCurrentActorProvider actorProvider;
    private InMemoryCoachingConversationRepository repository;
    private CoachingSessionApplicationService service;

    @BeforeEach
    void setUp() {
        actorProvider = new MutableCurrentActorProvider(ACTOR_A);
        repository = new InMemoryCoachingConversationRepository();
        service = new CoachingSessionApplicationService(
                actorProvider,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreateOwnedActiveSession() {
        CoachingSession session = service.createSession("Spring AI求职辅导");

        assertThat(session.ownerId()).isEqualTo(ACTOR_A);
        assertThat(session.status()).isEqualTo(CoachingSessionStatus.ACTIVE);
        assertThat(session.nextTurnSequence()).isEqualTo(1);
        assertThat(session.version()).isZero();
        assertThat(repository.findSession(ACTOR_A, session.sessionId())).contains(session);
        assertThat(repository.findSession(ACTOR_B, session.sessionId())).isEmpty();
    }

    @Test
    void shouldRecordPairedUserAndAssistantTurnsInSequence() {
        CoachingSession session = service.createSession("Memory设计辅导");

        ConversationTurn userTurn = service.recordUserTurn(
                session.sessionId(),
                session.version(),
                "Memory和聊天记录有什么区别？"
        );

        CoachingSession afterUserTurn = service.getSession(session.sessionId());

        ConversationTurn assistantTurn = service.recordValidatedAssistantTurn(
                session.sessionId(),
                afterUserTurn.version(),
                userTurn.turnId(),
                "聊天记录保存发生过的消息，长期Memory只包含用户确认的信息。",
                "agent-run-001"
        );

        CoachingSession afterAssistantTurn = service.getSession(session.sessionId());
        List<ConversationTurn> turns = service.getRecentTurns(session.sessionId());

        assertThat(userTurn.turnSequence()).isEqualTo(1);
        assertThat(assistantTurn.turnSequence()).isEqualTo(2);
        assertThat(assistantTurn.exchangeId()).isEqualTo(userTurn.exchangeId());
        assertThat(afterAssistantTurn.nextTurnSequence()).isEqualTo(3);
        assertThat(afterAssistantTurn.version()).isEqualTo(2);

        assertThat(turns)
                .extracting(ConversationTurn::role)
                .containsExactly(ConversationTurnRole.USER, ConversationTurnRole.ASSISTANT);

        assertThat(turns)
                .extracting(ConversationTurn::turnSequence)
                .containsExactly(1L, 2L);
    }

    @Test
    void shouldHideSessionAndTurnsFromAnotherActor() {
        CoachingSession session = service.createSession("用户A的会话");
        service.recordUserTurn(session.sessionId(), session.version(), "这是用户A的问题");

        actorProvider.switchTo(ACTOR_B);

        assertThatThrownBy(() -> service.getSession(session.sessionId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Session不存在或不属于当前用户");

        assertThatThrownBy(() -> service.getRecentTurns(session.sessionId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Session不存在或不属于当前用户");
    }

    @Test
    void shouldRejectNewTurnAfterSessionClosed() {
        CoachingSession session = service.createSession("准备关闭的会话");
        CoachingSession closed = service.closeSession(session.sessionId(), session.version());

        assertThat(closed.status()).isEqualTo(CoachingSessionStatus.CLOSED);
        assertThat(closed.closedAt()).isEqualTo(NOW);

        assertThatThrownBy(() -> service.recordUserTurn(
                closed.sessionId(),
                closed.version(),
                "关闭后不能继续提问"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("会话已经关闭");
    }

    @Test
    void shouldStoreControlledFailureWithoutModelOutput() {
        CoachingSession session = service.createSession("模型失败记录");
        ConversationTurn userTurn = service.recordUserTurn(
                session.sessionId(),
                session.version(),
                "请分析我的技能差距"
        );

        CoachingSession afterUserTurn = service.getSession(session.sessionId());

        ConversationTurn failedTurn = service.recordFailedAssistantTurn(
                session.sessionId(),
                afterUserTurn.version(),
                userTurn.turnId(),
                "agent-run-failed-001",
                "MODEL_OUTPUT_INVALID"
        );

        assertThat(failedTurn.role()).isEqualTo(ConversationTurnRole.ASSISTANT);
        assertThat(failedTurn.status()).isEqualTo(ConversationTurnStatus.FAILED);
        assertThat(failedTurn.content()).isNull();
        assertThat(failedTurn.contentHash()).isNull();
        assertThat(failedTurn.failureCode()).isEqualTo("MODEL_OUTPUT_INVALID");
        assertThat(failedTurn.isEligibleForMemoryExtraction()).isFalse();
    }

    @Test
    void shouldRejectAssistantResultUsingStaleSessionVersion() {
        CoachingSession session = service.createSession("并发消息测试");

        ConversationTurn firstUserTurn = service.recordUserTurn(
                session.sessionId(),
                session.version(),
                "第一个问题"
        );

        CoachingSession afterFirstQuestion = service.getSession(session.sessionId());

        service.recordUserTurn(
                session.sessionId(),
                afterFirstQuestion.version(),
                "第二个问题"
        );

        assertThatThrownBy(() -> service.recordValidatedAssistantTurn(
                session.sessionId(),
                afterFirstQuestion.version(),
                firstUserTurn.turnId(),
                "第一个问题的迟到回答",
                "agent-run-late"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session版本已经过期");

        assertThat(service.getRecentTurns(session.sessionId()))
                .extracting(ConversationTurn::role)
                .containsExactly(ConversationTurnRole.USER, ConversationTurnRole.USER);
    }

    @Test
    void shouldRejectAssistantLinkedToUserTurnFromAnotherSession() {
        CoachingSession firstSession = service.createSession("会话一");
        ConversationTurn firstUserTurn = service.recordUserTurn(
                firstSession.sessionId(),
                firstSession.version(),
                "会话一的问题"
        );

        CoachingSession secondSession = service.createSession("会话二");

        assertThatThrownBy(() -> service.recordValidatedAssistantTurn(
                secondSession.sessionId(),
                secondSession.version(),
                firstUserTurn.turnId(),
                "错误关联的回答",
                "agent-run-wrong-session"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户Turn不属于当前Session");
    }

    /**
     * 模拟owner、唯一序号和乐观锁规则，不模拟Spring/MySQL真实事务回滚。
     */
    private static final class InMemoryCoachingConversationRepository
            implements CoachingConversationRepository {

        private final Map<OwnedSessionKey, CoachingSession> sessions = new HashMap<>();
        private final Map<OwnedTurnKey, ConversationTurn> turns = new HashMap<>();

        @Override
        public void insertSession(CoachingSession session) {
            OwnedSessionKey key = new OwnedSessionKey(session.ownerId(), session.sessionId());

            if (sessions.putIfAbsent(key, session) != null) {
                throw new IllegalStateException("Session已经存在");
            }
        }

        @Override
        public Optional<CoachingSession> findSession(ActorId ownerId, UUID sessionId) {
            return Optional.ofNullable(sessions.get(new OwnedSessionKey(ownerId, sessionId)));
        }

        @Override
        public boolean updateSessionIfVersionMatches(
                ActorId ownerId,
                CoachingSession updatedSession,
                long expectedVersion
        ) {
            OwnedSessionKey key = new OwnedSessionKey(ownerId, updatedSession.sessionId());
            CoachingSession currentSession = sessions.get(key);

            if (currentSession == null || currentSession.version() != expectedVersion) {
                return false;
            }
            if (updatedSession.version() != expectedVersion + 1) {
                throw new IllegalArgumentException("更新后的Session版本非法");
            }

            sessions.put(key, updatedSession);
            return true;
        }

        @Override
        public void insertTurn(ConversationTurn turn) {
            OwnedSessionKey sessionKey = new OwnedSessionKey(turn.ownerId(), turn.sessionId());

            if (!sessions.containsKey(sessionKey)) {
                throw new IllegalStateException("Turn所属Session不存在");
            }

            boolean duplicateSequence = turns.values().stream()
                    .anyMatch(existing -> existing.sessionId().equals(turn.sessionId())
                            && existing.turnSequence() == turn.turnSequence());

            boolean duplicateExchangeRole = turns.values().stream()
                    .anyMatch(existing -> existing.sessionId().equals(turn.sessionId())
                            && existing.exchangeId().equals(turn.exchangeId())
                            && existing.role() == turn.role());

            if (duplicateSequence) {
                throw new IllegalStateException("会话内turnSequence重复");
            }
            if (duplicateExchangeRole) {
                throw new IllegalStateException("同一exchangeId的消息角色重复");
            }

            OwnedTurnKey turnKey = new OwnedTurnKey(turn.ownerId(), turn.turnId());

            if (turns.putIfAbsent(turnKey, turn) != null) {
                throw new IllegalStateException("Turn已经存在");
            }
        }

        @Override
        public Optional<ConversationTurn> findTurn(ActorId ownerId, UUID turnId) {
            return Optional.ofNullable(turns.get(new OwnedTurnKey(ownerId, turnId)));
        }

        @Override
        public List<ConversationTurn> findRecentTurns(ActorId ownerId, UUID sessionId, int limit) {
            if (limit < 1) {
                throw new IllegalArgumentException("limit必须大于0");
            }

            List<ConversationTurn> orderedTurns = turns.values().stream()
                    .filter(turn -> turn.ownerId().equals(ownerId))
                    .filter(turn -> turn.sessionId().equals(sessionId))
                    .sorted(Comparator.comparingLong(ConversationTurn::turnSequence))
                    .toList();

            int fromIndex = Math.max(0, orderedTurns.size() - limit);
            return new ArrayList<>(orderedTurns.subList(fromIndex, orderedTurns.size()));
        }

        private record OwnedSessionKey(ActorId ownerId, UUID sessionId) {
        }

        private record OwnedTurnKey(ActorId ownerId, UUID turnId) {
        }
    }
}