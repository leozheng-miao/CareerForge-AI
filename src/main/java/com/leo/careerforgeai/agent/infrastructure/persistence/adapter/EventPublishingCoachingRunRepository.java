package com.leo.careerforgeai.agent.infrastructure.persistence.adapter;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEvent;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
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
 * @description: 为新Run和成功CAS状态更新注册事务提交后安全事件
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@Primary
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class EventPublishingCoachingRunRepository implements CoachingRunRepository {

    private final MyBatisPlusCoachingRunAdapter delegate;
    private final ApplicationEventPublisher eventPublisher;

    public EventPublishingCoachingRunRepository(
            MyBatisPlusCoachingRunAdapter delegate,
            ApplicationEventPublisher eventPublisher
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为空");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher不能为空");
    }

    @Override
    public CoachingRun claim(CoachingRun candidate) {
        CoachingRun claimed = delegate.claim(candidate);
        if (candidate.runId().equals(claimed.runId())) publishStateEvent(claimed);
        return claimed;
    }

    @Override
    public Optional<CoachingRun> findByRunId(ActorId ownerId, UUID runId) {
        return delegate.findByRunId(ownerId, runId);
    }

    @Override
    public Optional<CoachingRun> findByRequestId(ActorId ownerId, UUID requestId) {
        return delegate.findByRequestId(ownerId, requestId);
    }

    @Override
    public List<CoachingRun> findNonTerminalUpdatedBefore(Instant updatedBefore, int limit) {
        return delegate.findNonTerminalUpdatedBefore(updatedBefore, limit);
    }

    @Override
    public boolean updateIfVersionMatches(ActorId ownerId, CoachingRun updatedRun, long expectedVersion) {
        boolean updated = delegate.updateIfVersionMatches(ownerId, updatedRun, expectedVersion);
        if (updated) publishStateEvent(updatedRun);
        return updated;
    }

    private void publishStateEvent(CoachingRun run) {
        eventPublisher.publishEvent(
                CoachingRunEvent.runState(
                        run.ownerId(),
                        run.runId(),
                        run.status(),
                        run.updatedAt()
                )
        );
    }
}