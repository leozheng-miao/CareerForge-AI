package com.leo.careerforgeai.agent.application.run.event;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunEventStore;
import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.CoachingRunNotFoundException;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 先校验MySQL Run归属，再读取可丢失的Redis短期事件
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunEventQueryApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingRunRepository repository;
    private final CoachingRunEventStore eventStore;

    public CoachingRunEventQueryApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingRunRepository repository,
            CoachingRunEventStore eventStore
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore不能为空");
    }

    public CoachingRunEventObservation observe(UUID runId, String lastEventId, int limit) {
        return observeForActor(currentActorProvider.currentActor(), runId, lastEventId, limit);
    }

    public CoachingRunEventObservation observeForActor(
            ActorId ownerId,
            UUID runId,
            String lastEventId,
            int limit
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(runId, "runId不能为空");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit必须在1到1000之间");

        CoachingRun run = repository.findByRunId(ownerId, runId)
                .orElseThrow(() -> new CoachingRunNotFoundException(runId));

        try {
            return new CoachingRunEventObservation(
                    run,
                    eventStore.readAfter(ownerId, runId, lastEventId, limit),
                    null
            );
        } catch (RedisInfrastructureException exception) {
            return new CoachingRunEventObservation(run, List.of(), exception.errorType());
        }
    }
}