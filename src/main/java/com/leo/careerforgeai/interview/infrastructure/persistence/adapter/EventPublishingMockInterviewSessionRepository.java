package com.leo.careerforgeai.interview.infrastructure.persistence.adapter;

import com.leo.careerforgeai.interview.application.event.InterviewEvent;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 装饰MySQL面试Repository并在创建或CAS成功后发布安全状态事件
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Primary
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class EventPublishingMockInterviewSessionRepository implements MockInterviewSessionRepository {

    private final MyBatisPlusMockInterviewSessionAdapter delegate;
    private final ApplicationEventPublisher eventPublisher;

    public EventPublishingMockInterviewSessionRepository(MyBatisPlusMockInterviewSessionAdapter delegate,
                                                         ApplicationEventPublisher eventPublisher) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为空");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher不能为空");
    }

    @Override
    public MockInterviewSession claim(MockInterviewSession candidate) {
        MockInterviewSession claimed = delegate.claim(candidate);
        if (candidate.interviewId().equals(claimed.interviewId())) publishStateEvent(claimed);
        return claimed;
    }

    @Override
    public Optional<MockInterviewSession> findById(ActorId ownerId, UUID interviewId) {
        return delegate.findById(ownerId, interviewId);
    }

    @Override
    public Optional<MockInterviewSession> findByRequestId(ActorId ownerId, UUID requestId) {
        return delegate.findByRequestId(ownerId, requestId);
    }

    @Override
    public boolean updateIfVersionMatches(ActorId ownerId,
                                          MockInterviewSession updatedSession,
                                          long expectedVersion) {
        boolean updated = delegate.updateIfVersionMatches(ownerId, updatedSession, expectedVersion);
        if (updated) publishStateEvent(updatedSession);
        return updated;
    }

    @Override
    public List<MockInterviewSession> findExecutionRequiredUpdatedBefore(ActorId ownerId, Instant updatedBefore, int limit) {
        return delegate.findExecutionRequiredUpdatedBefore(ownerId, updatedBefore, limit);
    }

    private void publishStateEvent(MockInterviewSession session) {
        eventPublisher.publishEvent(InterviewEvent.state(
                session.ownerId(),
                session.interviewId(),
                session.status(),
                session.updatedAt()
        ));
    }
}