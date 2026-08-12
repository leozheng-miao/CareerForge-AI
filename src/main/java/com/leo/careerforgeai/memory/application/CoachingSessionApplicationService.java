package com.leo.careerforgeai.memory.application;

import com.leo.careerforgeai.memory.application.port.CoachingConversationRepository;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnRole;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 管理Coaching Session生命周期并原子保存用户、助手和失败Conversation Turn
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingSessionApplicationService {

    public static final int DEFAULT_RECENT_TURN_LIMIT = 20;

    private final CurrentActorProvider currentActorProvider;
    private final CoachingConversationRepository conversationRepository;
    private final Clock clock;

    public CoachingSessionApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingConversationRepository conversationRepository,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider 不能为空");
        this.conversationRepository = Objects.requireNonNull(conversationRepository, "conversationRepository 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /** 使用服务端当前Actor创建新的ACTIVE会话。 */
    @Transactional
    public CoachingSession createSession(String title) {
        ActorId actorId = currentActorProvider.currentActor();
        Instant now = clock.instant();
        CoachingSession session = CoachingSession.create(UUID.randomUUID(), actorId, title, now);

        conversationRepository.insertSession(session);
        return session;
    }

    /** 读取当前Actor拥有的会话，不暴露其他用户的会话是否存在。 */
    @Transactional(readOnly = true)
    public CoachingSession getSession(UUID sessionId) {
        ActorId actorId = currentActorProvider.currentActor();
        return requireOwnedSession(actorId, sessionId);
    }

    /** 关闭当前Actor拥有的ACTIVE会话。 */
    @Transactional
    public CoachingSession closeSession(UUID sessionId, long expectedVersion) {
        ActorId actorId = currentActorProvider.currentActor();
        CoachingSession session = requireOwnedSession(actorId, sessionId);

        requireExpectedVersion(session, expectedVersion);

        CoachingSession closedSession = session.close(clock.instant());
        updateSessionOrThrow(actorId, closedSession, expectedVersion);

        return closedSession;
    }

    /**
     * 保存用户消息并原子占用会话内Turn序号。
     * 返回值中的exchangeId供后续助手Turn与该用户问题关联。
     */
    @Transactional
    public ConversationTurn recordUserTurn(UUID sessionId, long expectedSessionVersion, String content) {
        ActorId actorId = currentActorProvider.currentActor();
        CoachingSession session = requireOwnedSession(actorId, sessionId);

        requireExpectedVersion(session, expectedSessionVersion);

        Instant now = clock.instant();
        long turnSequence = session.nextTurnSequence();
        UUID exchangeId = UUID.randomUUID();

        ConversationTurn userTurn = ConversationTurn.completedUser(
                UUID.randomUUID(),
                session.sessionId(),
                exchangeId,
                actorId,
                turnSequence,
                content,
                now
        );

        CoachingSession advancedSession = session.advanceTurnSequence(now);
        updateSessionOrThrow(actorId, advancedSession, expectedSessionVersion);
        conversationRepository.insertTurn(userTurn);

        return userTurn;
    }

    /**
     * 保存已经通过Career Coach最终回答校验的助手消息。
     * 该方法不能接收未经FinalAnswerValidator校验的模型原始输出。
     */
    @Transactional
    public ConversationTurn recordValidatedAssistantTurn(
            UUID sessionId,
            long expectedSessionVersion,
            UUID userTurnId,
            String validatedContent,
            String agentRunId
    ) {
        ActorId actorId = currentActorProvider.currentActor();
        CoachingSession session = requireOwnedSession(actorId, sessionId);

        requireExpectedVersion(session, expectedSessionVersion);

        ConversationTurn userTurn = requireCompletedUserTurn(actorId, sessionId, userTurnId);
        Instant now = clock.instant();

        ConversationTurn assistantTurn = ConversationTurn.completedAssistant(
                UUID.randomUUID(),
                session.sessionId(),
                userTurn.exchangeId(),
                actorId,
                session.nextTurnSequence(),
                validatedContent,
                agentRunId,
                now
        );

        CoachingSession advancedSession = session.advanceTurnSequence(now);
        updateSessionOrThrow(actorId, advancedSession, expectedSessionVersion);
        conversationRepository.insertTurn(assistantTurn);

        return assistantTurn;
    }

    /**
     * 保存受控助手失败记录。
     * 只保存稳定failureCode，不保存非法、超时或未通过校验的模型输出。
     */
    @Transactional
    public ConversationTurn recordFailedAssistantTurn(
            UUID sessionId,
            long expectedSessionVersion,
            UUID userTurnId,
            String agentRunId,
            String failureCode
    ) {
        ActorId actorId = currentActorProvider.currentActor();
        CoachingSession session = requireOwnedSession(actorId, sessionId);

        requireExpectedVersion(session, expectedSessionVersion);

        ConversationTurn userTurn = requireCompletedUserTurn(actorId, sessionId, userTurnId);
        Instant now = clock.instant();

        ConversationTurn failedTurn = ConversationTurn.failedAssistant(
                UUID.randomUUID(),
                session.sessionId(),
                userTurn.exchangeId(),
                actorId,
                session.nextTurnSequence(),
                agentRunId,
                failureCode,
                now
        );

        CoachingSession advancedSession = session.advanceTurnSequence(now);
        updateSessionOrThrow(actorId, advancedSession, expectedSessionVersion);
        conversationRepository.insertTurn(failedTurn);

        return failedTurn;
    }

    /**
     * 读取当前会话最近的消息。
     * 这里只返回历史记录，完整问答筛选和Context预算在后续Context Assembler完成。
     */
    @Transactional(readOnly = true)
    public List<ConversationTurn> getRecentTurns(UUID sessionId) {
        ActorId actorId = currentActorProvider.currentActor();
        requireOwnedSession(actorId, sessionId);

        return conversationRepository.findRecentTurns(actorId, sessionId, DEFAULT_RECENT_TURN_LIMIT);
    }

    private CoachingSession requireOwnedSession(ActorId actorId, UUID sessionId) {
        Objects.requireNonNull(actorId, "actorId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");

        return conversationRepository.findSession(actorId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session不存在或不属于当前用户"));
    }

    private ConversationTurn requireCompletedUserTurn(ActorId actorId, UUID sessionId, UUID userTurnId) {
        Objects.requireNonNull(userTurnId, "userTurnId 不能为空");

        ConversationTurn userTurn = conversationRepository.findTurn(actorId, userTurnId)
                .orElseThrow(() -> new IllegalArgumentException("用户Turn不存在或不属于当前用户"));

        if (!userTurn.sessionId().equals(sessionId)) {
            throw new IllegalArgumentException("用户Turn不属于当前Session");
        }
        if (userTurn.role() != ConversationTurnRole.USER) {
            throw new IllegalArgumentException("关联Turn必须是USER消息");
        }
        if (userTurn.status() != ConversationTurnStatus.COMPLETED) {
            throw new IllegalArgumentException("关联用户Turn必须是COMPLETED状态");
        }

        return userTurn;
    }

    private static void requireExpectedVersion(CoachingSession session, long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion不能小于0");
        }
        if (session.version() != expectedVersion) {
            throw new IllegalStateException("Session版本已经过期");
        }
    }

    private void updateSessionOrThrow(
            ActorId actorId,
            CoachingSession updatedSession,
            long expectedVersion
    ) {
        boolean updated = conversationRepository.updateSessionIfVersionMatches(
                actorId,
                updatedSession,
                expectedVersion
        );

        if (!updated) {
            throw new IllegalStateException("Session并发更新冲突");
        }
    }
}