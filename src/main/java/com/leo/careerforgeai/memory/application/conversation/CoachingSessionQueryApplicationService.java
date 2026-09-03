package com.leo.careerforgeai.memory.application.conversation;

import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingSessionQueryRepository;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSessionStatus;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 查询当前用户的Session和Turn并生成不透明稳定Cursor
 * @author: Miao Zheng
 * @date: 2026-09-03
 */
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingSessionQueryApplicationService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private final CurrentActorProvider currentActorProvider;
    private final CoachingSessionQueryRepository queryRepository;
    private final CoachingConversationRepository conversationRepository;

    public CoachingSessionQueryApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingSessionQueryRepository queryRepository,
            CoachingConversationRepository conversationRepository
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.queryRepository = Objects.requireNonNull(queryRepository, "queryRepository不能为空");
        this.conversationRepository = Objects.requireNonNull(conversationRepository, "conversationRepository不能为空");
    }

    @Transactional(readOnly = true)
    public SessionPage list(String cursor, CoachingSessionStatus status, int limit) {
        requireLimit(limit);
        SessionCursor decoded = decodeSessionCursor(cursor);
        List<CoachingSession> rows = queryRepository.findSessionPage(
                currentActorProvider.currentActor(),
                status,
                decoded == null ? null : decoded.updatedAt(),
                decoded == null ? null : decoded.sessionId(),
                limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<CoachingSession> items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        return new SessionPage(items, hasMore ? encodeSession(items.getLast()) : null, hasMore);
    }

    @Transactional(readOnly = true)
    public TurnPage listTurns(UUID sessionId, String cursor, int limit) {
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        requireLimit(limit);
        ActorId ownerId = currentActorProvider.currentActor();
        conversationRepository.findSession(ownerId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session不存在或不属于当前用户"));

        TurnCursor decoded = decodeTurnCursor(cursor);
        if (decoded != null && !decoded.sessionId().equals(sessionId)) throw invalidCursor();
        List<ConversationTurn> rows = queryRepository.findTurnPage(
                ownerId,
                sessionId,
                decoded == null ? null : decoded.beforeTurnSequence(),
                limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<ConversationTurn> descending = new ArrayList<>(
                rows.subList(0, Math.min(limit, rows.size()))
        );
        String nextCursor = hasMore ? encodeTurn(descending.getLast()) : null;
        Collections.reverse(descending);
        return new TurnPage(descending, nextCursor, hasMore);
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit必须在1到" + MAX_PAGE_SIZE + "之间");
        }
    }

    private static String encodeSession(CoachingSession session) {
        return encode(session.updatedAt() + "|" + session.sessionId());
    }

    private static String encodeTurn(ConversationTurn turn) {
        return encode(turn.sessionId() + "|" + turn.turnSequence());
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static SessionCursor decodeSessionCursor(String cursor) {
        if (cursor == null) return null;
        try {
            String[] parts = decode(cursor).split("\\|", -1);
            if (parts.length != 2) throw invalidCursor();
            return new SessionCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private static TurnCursor decodeTurnCursor(String cursor) {
        if (cursor == null) return null;
        try {
            String[] parts = decode(cursor).split("\\|", -1);
            if (parts.length != 2) throw invalidCursor();
            return new TurnCursor(UUID.fromString(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private static String decode(String cursor) {
        if (cursor.isBlank() || cursor.length() > 256) throw invalidCursor();
        return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("cursor格式不合法");
    }

    /**
     * @program: CareerForge-AI
     * @description: Session分页结果
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页会话
     * @param nextCursor 下一页Cursor
     * @param hasMore 是否存在下一页
     */
    public record SessionPage(List<CoachingSession> items, String nextCursor, boolean hasMore) {
        public SessionPage {
            items = List.copyOf(Objects.requireNonNull(items, "items不能为空"));
            if (hasMore != (nextCursor != null)) throw new IllegalArgumentException("分页状态不一致");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: Turn历史分页结果
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页Turn，按turnSequence升序返回
     * @param nextCursor 更早一页Cursor
     * @param hasMore 是否还有更早的Turn
     */
    public record TurnPage(List<ConversationTurn> items, String nextCursor, boolean hasMore) {
        public TurnPage {
            items = List.copyOf(Objects.requireNonNull(items, "items不能为空"));
            if (hasMore != (nextCursor != null)) throw new IllegalArgumentException("分页状态不一致");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: Session分页位置
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param updatedAt 最后更新时间
     * @param sessionId 会话ID
     */
    private record SessionCursor(Instant updatedAt, UUID sessionId) {
        private SessionCursor {
            Objects.requireNonNull(updatedAt, "updatedAt不能为空");
            Objects.requireNonNull(sessionId, "sessionId不能为空");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 绑定具体Session的Turn分页位置
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param sessionId 会话ID
     * @param beforeTurnSequence 下一页必须早于的Turn序号
     */
    private record TurnCursor(UUID sessionId, long beforeTurnSequence) {
        private TurnCursor {
            Objects.requireNonNull(sessionId, "sessionId不能为空");
            if (beforeTurnSequence < 1) throw invalidCursor();
        }
    }
}