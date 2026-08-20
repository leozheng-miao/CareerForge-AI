package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用短事务将非终态Coaching Run原子收敛为INTERRUPTED
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunInterruptionApplicationService {

    private final CoachingRunRepository repository;
    private final Clock clock;

    public CoachingRunInterruptionApplicationService(
            CoachingRunRepository repository,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public CoachingRun interruptForActor(
            ActorId ownerId,
            UUID runId,
            String failureCode
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(runId, "runId不能为空");

        CoachingRun current = repository.findByRunId(ownerId, runId)
                .orElseThrow(() -> new CoachingRunNotFoundException(runId));

        if (current.isTerminal()) return current;

        CoachingRun interrupted = current.interrupt(failureCode, clock.instant());
        if (!repository.updateIfVersionMatches(ownerId, interrupted, current.version())) {
            throw new CoachingRunVersionConflictException(runId, current.version());
        }
        return interrupted;
    }
}