package com.leo.careerforgeai.agent.application.run.submission;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRateLimiter;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncTask;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunCapacityRejectedException;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunLifecycleApplicationService;
import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitDecision;
import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitExceededException;
import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitUnavailableException;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunDispatchRejectedException;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionVersionConflictException;

/**
 * @program: CareerForge-AI
 * @description: 编排Run幂等认领、原子限流、容量准入、接受和异步虚拟线程执行
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunAsyncSubmissionApplicationService {

    private static final String RATE_LIMITED_FAILURE = "RATE_LIMITED";
    private static final String RATE_LIMIT_UNAVAILABLE_FAILURE = "RATE_LIMIT_UNAVAILABLE";
    private static final String LOCAL_CAPACITY_REJECTED_FAILURE = "LOCAL_CAPACITY_REJECTED";
    private static final String EXECUTOR_NOT_ACCEPTING_FAILURE = "EXECUTOR_NOT_ACCEPTING";
    private static final String SESSION_VERSION_DRIFT_FAILURE = "SESSION_VERSION_DRIFT";

    private final CurrentActorProvider currentActorProvider;
    private final CoachingRunClaimApplicationService claimService;
    private final CoachingRunAcceptanceApplicationService acceptanceService;
    private final CoachingRunLifecycleApplicationService lifecycleService;
    private final CoachingRunRateLimiter rateLimiter;
    private final CoachingRunAsyncTask asyncTask;
    private final CoachingRunAsyncDispatcher dispatcher;
    private final CoachingRunExecutionProperties properties;
    private final Clock clock;

    public CoachingRunAsyncSubmissionApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingRunClaimApplicationService claimService,
            CoachingRunAcceptanceApplicationService acceptanceService,
            CoachingRunLifecycleApplicationService lifecycleService,
            CoachingRunRateLimiter rateLimiter,
            CoachingRunAsyncTask asyncTask,
            CoachingRunAsyncDispatcher dispatcher,
            CoachingRunExecutionProperties properties,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.claimService = Objects.requireNonNull(claimService, "claimService不能为空");
        this.acceptanceService = Objects.requireNonNull(acceptanceService, "acceptanceService不能为空");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService不能为空");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter不能为空");
        this.asyncTask = Objects.requireNonNull(asyncTask, "asyncTask不能为空");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher不能为空");
        this.properties = Objects.requireNonNull(properties, "properties不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public CoachingRun submit(
            UUID sessionId,
            UUID requestId,
            long expectedSessionVersion,
            String message
    ) {
        ActorId ownerId = currentActorProvider.currentActor();
        CoachingRunClaimResult claimResult = claimService.claim(
                sessionId,
                requestId,
                expectedSessionVersion,
                message
        );
        CoachingRun claimed = claimResult.run();

        requireOwner(ownerId, claimed);
        if (claimResult.replayed() && claimed.status() != CoachingRunStatus.RECEIVED) return claimed;
        if (!claimResult.replayed()) enforceRateLimit(ownerId, claimed.runId());

        RunAdmissionLease lease = acquireLocalCapacity(ownerId, claimed.runId());
        boolean handedOff = false;

        try {
            CoachingRun accepted;

            try {
                accepted = acceptanceService.accept(claimed.runId(), message);
            } catch (CoachingSessionVersionConflictException exception) {
                CoachingRun converged = convergeAfterSessionConflict(
                        ownerId,
                        claimed.runId(),
                        exception
                );
                if (converged != null && converged.status() != CoachingRunStatus.REJECTED) {
                    return converged;
                }
                throw exception;
            }

            requireOwner(ownerId, accepted);
            if (accepted.status() != CoachingRunStatus.ACCEPTED) return accepted;
            Instant submittedAt = clock.instant();
            RunExecutionContext context = new RunExecutionContext(
                    ownerId,
                    accepted.runId(),
                    accepted.runId().toString(),
                    submittedAt,
                    submittedAt.plus(properties.executionTimeout())
            );

            try {
                dispatcher.dispatch(context, lease, asyncTask::execute);
                handedOff = true;
                return accepted;
            } catch (CoachingRunDispatchRejectedException exception) {
                lifecycleService.rejectForActor(
                        ownerId,
                        accepted.runId(),
                        EXECUTOR_NOT_ACCEPTING_FAILURE
                );
                throw exception;
            }
        } finally {
            if (!handedOff) lease.close();
        }
    }

    private void enforceRateLimit(ActorId ownerId, UUID runId) {
        CoachingRunRateLimitDecision decision;
        try {
            decision = rateLimiter.acquire(ownerId);
        } catch (RedisInfrastructureException exception) {
            lifecycleService.rejectForActor(ownerId, runId, RATE_LIMIT_UNAVAILABLE_FAILURE);
            throw new CoachingRunRateLimitUnavailableException(ownerId, runId, exception.errorType());
        }

        if (decision.allowed()) return;

        lifecycleService.rejectForActor(ownerId, runId, RATE_LIMITED_FAILURE);
        throw new CoachingRunRateLimitExceededException(ownerId, runId, decision.resetAfter());
    }

    private RunAdmissionLease acquireLocalCapacity(ActorId ownerId, UUID runId) {
        try {
            return dispatcher.acquire(ownerId);
        } catch (CoachingRunCapacityRejectedException exception) {
            lifecycleService.rejectForActor(ownerId, runId, LOCAL_CAPACITY_REJECTED_FAILURE);
            throw exception;
        }
    }

    private static void requireOwner(ActorId expectedOwner, CoachingRun run) {
        if (!expectedOwner.equals(run.ownerId())) {
            throw new IllegalStateException("Run不属于当前执行用户");
        }
    }

    private CoachingRun convergeAfterSessionConflict(
            ActorId ownerId,
            UUID runId,
            CoachingSessionVersionConflictException original
    ) {
        try {
            return lifecycleService.rejectReceivedForActor(
                    ownerId,
                    runId,
                    SESSION_VERSION_DRIFT_FAILURE
            );
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
            return null;
        }
    }
}