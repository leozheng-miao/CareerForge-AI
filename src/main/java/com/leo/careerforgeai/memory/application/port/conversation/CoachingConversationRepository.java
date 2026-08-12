package com.leo.careerforgeai.memory.application.port.conversation;

import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义会话和Conversation Turn的持久化端口并统一强制owner访问边界
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public interface CoachingConversationRepository {

    /** 保存服务端创建的新会话。 */
    void insertSession(CoachingSession session);

    /** 使用ownerId和sessionId共同查询会话。 */
    Optional<CoachingSession> findSession(ActorId ownerId, UUID sessionId);

    /** 使用owner和旧version更新会话序号或关闭状态。 */
    boolean updateSessionIfVersionMatches(
            ActorId ownerId,
            CoachingSession updatedSession,
            long expectedVersion
    );

    /** 保存已经通过领域校验的Conversation Turn。 */
    void insertTurn(ConversationTurn turn);

    /** 使用ownerId和turnId共同查询消息。 */
    Optional<ConversationTurn> findTurn(ActorId ownerId, UUID turnId);

    /**
     * 查询当前用户会话最近的消息，并按turnSequence升序返回。
     * limit必须由服务端配置控制，不能直接信任客户端值。
     */
    List<ConversationTurn> findRecentTurns(ActorId ownerId, UUID sessionId, int limit);
}