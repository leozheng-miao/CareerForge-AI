package com.leo.careerforgeai.interview.application.session;

import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.session.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * @program: CareerForge-AI
 * @description: 使用CurrentActor、expectedVersion和数据库CAS推进模拟面试生命周期
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
@Service
public class MockInterviewLifecycleApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository repository;
    private final Clock clock;

    public MockInterviewLifecycleApplicationService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository repository,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public MockInterviewSession get(UUID interviewId) {
        return requireOwnedSession(currentActor(), interviewId);
    }

    public MockInterviewSession startQuestionGeneration(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.startQuestionGeneration(clock.instant()));
    }

    public MockInterviewSession waitForAnswer(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.waitForAnswer(clock.instant()));
    }

    public MockInterviewSession startReview(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.startReview(clock.instant()));
    }

    public MockInterviewSession continueQuestioning(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.continueQuestioning(clock.instant()));
    }

    public MockInterviewSession startReportGeneration(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.startReportGeneration(clock.instant()));
    }

    public MockInterviewSession awaitConfirmation(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.awaitConfirmation(clock.instant()));
    }

    public MockInterviewSession complete(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.complete(clock.instant()));
    }

    public MockInterviewSession fail(
            UUID interviewId,
            long expectedVersion,
            InterviewFailureCode failureCode
    ) {
        Objects.requireNonNull(failureCode, "failureCode不能为空");
        return mutate(interviewId, expectedVersion, session -> session.fail(failureCode, clock.instant()));
    }

    public MockInterviewSession interrupt(
            UUID interviewId,
            long expectedVersion,
            InterviewFailureCode failureCode
    ) {
        Objects.requireNonNull(failureCode, "failureCode不能为空");
        return mutate(interviewId, expectedVersion, session -> session.interrupt(failureCode, clock.instant()));
    }

    public MockInterviewSession retryReportGeneration(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.retryReportGeneration(clock.instant()));
    }

    public MockInterviewSession cancel(UUID interviewId, long expectedVersion) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion不能小于0");

        ActorId ownerId = currentActor();
        MockInterviewSession current = requireOwnedSession(ownerId, interviewId);
        if (current.status() == InterviewStatus.CANCELLED) return current;
        if (current.isTerminal()) {
            throw new MockInterviewCancellationConflictException(interviewId, current.status());
        }
        if (current.version() != expectedVersion) {
            throw new MockInterviewVersionConflictException(interviewId, expectedVersion);
        }

        MockInterviewSession cancelled = current.cancel(clock.instant());
        if (!repository.updateIfVersionMatches(ownerId, cancelled, expectedVersion)) {
            throw new MockInterviewVersionConflictException(interviewId, expectedVersion);
        }
        return cancelled;
    }

    private MockInterviewSession mutate(
            UUID interviewId,
            long expectedVersion,
            UnaryOperator<MockInterviewSession> mutation
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(mutation, "mutation不能为空");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion不能小于0");

        ActorId ownerId = currentActor();
        MockInterviewSession current = requireOwnedSession(ownerId, interviewId);

        if (current.version() != expectedVersion) {
            throw new MockInterviewVersionConflictException(interviewId, expectedVersion);
        }

        MockInterviewSession updated = mutation.apply(current);
        if (!repository.updateIfVersionMatches(ownerId, updated, expectedVersion)) {
            throw new MockInterviewVersionConflictException(interviewId, expectedVersion);
        }
        return updated;
    }

    private MockInterviewSession requireOwnedSession(ActorId ownerId, UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");

        MockInterviewSession session = repository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));

        if (!ownerId.equals(session.ownerId())) {
            throw new MockInterviewNotFoundException(interviewId);
        }
        return session;
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }

    public SessionPage list(InterviewStatus status, String cursor, int limit) {
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("limit必须在1到20之间");
        SessionCursor decoded = decodeCursor(cursor);
        String statusKey = status == null ? "*" : status.name();
        if (decoded != null && !decoded.statusKey().equals(statusKey)) {
            throw new IllegalArgumentException("cursor与当前面试状态过滤不匹配");
        }

        List<MockInterviewSession> rows = repository.findPage(
                currentActor(), status,
                decoded == null ? null : decoded.beforeUpdatedAt(),
                decoded == null ? null : decoded.beforeInterviewId(),
                limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<MockInterviewSession> items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        return new SessionPage(items, hasMore ? encodeCursor(items.getLast(), statusKey) : null, hasMore);
    }

    private static String encodeCursor(MockInterviewSession session, String statusKey) {
        String value = statusKey + "|" + session.updatedAt() + "|" + session.interviewId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static SessionCursor decodeCursor(String cursor) {
        if (cursor == null) return null;
        if (cursor.isBlank() || cursor.length() > 256) throw invalidCursor();
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3 || parts[0].isBlank()) throw invalidCursor();
            return new SessionCursor(parts[0], Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("cursor格式不合法");
    }

    /**
     * @program: CareerForge-AI
     * @description: 当前用户模拟面试历史分页结果
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页面试
     * @param nextCursor 下一页Cursor
     * @param hasMore 是否存在下一页
     */
    public record SessionPage(List<MockInterviewSession> items, String nextCursor, boolean hasMore) {
        public SessionPage {
            items = List.copyOf(Objects.requireNonNull(items, "items不能为空"));
            if (hasMore != (nextCursor != null)) throw new IllegalArgumentException("分页状态不一致");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 与状态过滤绑定的模拟面试分页位置
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param statusKey 状态过滤标识
     * @param beforeUpdatedAt 下一页更新时间上界
     * @param beforeInterviewId 同一更新时间下面试ID上界
     */
    private record SessionCursor(String statusKey, Instant beforeUpdatedAt, UUID beforeInterviewId) {
        private SessionCursor {
            if (statusKey == null || statusKey.isBlank()
                    || beforeUpdatedAt == null || beforeInterviewId == null) throw invalidCursor();
        }
    }
}