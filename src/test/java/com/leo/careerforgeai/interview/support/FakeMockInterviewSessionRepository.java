package com.leo.careerforgeai.interview.support;

import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;


/**
 * @program: CareerForge-AI
 * @description: 为模拟面试应用层测试提供owner隔离和CAS语义的内存Repository
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public final class FakeMockInterviewSessionRepository implements MockInterviewSessionRepository {

    private final Map<UUID, MockInterviewSession> sessions = new HashMap<>();
    private boolean forceNextConflict;

    public void save(MockInterviewSession session) {
        sessions.put(session.interviewId(), session);
    }

    public MockInterviewSession findStored(UUID interviewId) {
        return sessions.get(interviewId);
    }

    public void forceNextConflict() {
        forceNextConflict = true;
    }

    @Override
    public MockInterviewSession claim(MockInterviewSession candidate) {
        Optional<MockInterviewSession> existing = findByRequestId(candidate.ownerId(), candidate.requestId());
        if (existing.isPresent()) return existing.get();
        if (sessions.containsKey(candidate.interviewId())) {
            throw new IllegalStateException("interviewId冲突");
        }
        sessions.put(candidate.interviewId(), candidate);
        return candidate;
    }

    @Override
    public Optional<MockInterviewSession> findByRequestId(ActorId ownerId, UUID requestId) {
        return sessions.values().stream()
                .filter(session -> session.ownerId().equals(ownerId))
                .filter(session -> session.requestId().equals(requestId))
                .findFirst();
    }

    @Override
    public Optional<MockInterviewSession> findById(ActorId ownerId, UUID interviewId) {
        return Optional.ofNullable(sessions.get(interviewId)).filter(session -> session.ownerId().equals(ownerId));
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            MockInterviewSession updatedSession,
            long expectedVersion
    ) {
        if (forceNextConflict) {
            forceNextConflict = false;
            return false;
        }

        MockInterviewSession current = sessions.get(updatedSession.interviewId());
        if (current == null
                || !current.ownerId().equals(ownerId)
                || !updatedSession.ownerId().equals(ownerId)
                || current.version() != expectedVersion
                || updatedSession.version() != expectedVersion + 1) {
            return false;
        }

        sessions.put(updatedSession.interviewId(), updatedSession);
        return true;
    }

    @Override
    public List<MockInterviewSession> findSystemRecoveryCandidatesUpdatedBefore(
            Instant updatedBefore,
            int limit
    ) {
        Objects.requireNonNull(updatedBefore, "updatedBefore不能为空");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit必须在1到1000之间");

        return sessions.values().stream()
                .filter(session -> session.updatedAt().isBefore(updatedBefore))
                .filter(session -> session.status() == InterviewStatus.GENERATING_QUESTION
                        || session.status() == InterviewStatus.REVIEWING
                        || session.status() == InterviewStatus.GENERATING_REPORT)
                .sorted(Comparator.comparing(MockInterviewSession::updatedAt)
                        .thenComparing(MockInterviewSession::interviewId))
                .limit(limit)
                .toList();
    }

    @Override
    public List<MockInterviewSession> findPage(
            ActorId ownerId,
            InterviewStatus status,
            Instant beforeUpdatedAt,
            UUID beforeInterviewId,
            int limit
    ) {
        return sessions.values().stream()
                .filter(session -> session.ownerId().equals(ownerId))
                .filter(session -> status == null || session.status() == status)
                .filter(session -> beforeUpdatedAt == null
                        || session.updatedAt().isBefore(beforeUpdatedAt)
                        || session.updatedAt().equals(beforeUpdatedAt)
                        && session.interviewId().compareTo(beforeInterviewId) < 0)
                .sorted(Comparator.comparing(MockInterviewSession::updatedAt)
                        .thenComparing(MockInterviewSession::interviewId).reversed())
                .limit(limit)
                .toList();
    }
}