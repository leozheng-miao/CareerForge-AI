package com.leo.careerforgeai.agent.application.run.submission;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncTask;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 编排Run幂等认领、容量准入、接受和异步虚拟线程执行
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunAsyncSubmissionApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingRunClaimApplicationService claimService;
    private final CoachingRunAcceptanceApplicationService acceptanceService;
    private final CoachingRunAsyncTask asyncTask;
    private final CoachingRunAsyncDispatcher dispatcher;
    private final CoachingRunExecutionProperties properties;
    private final Clock clock;

    public CoachingRunAsyncSubmissionApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingRunClaimApplicationService claimService,
            CoachingRunAcceptanceApplicationService acceptanceService,
            CoachingRunAsyncTask asyncTask,
            CoachingRunAsyncDispatcher dispatcher,
            CoachingRunExecutionProperties properties,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.claimService = Objects.requireNonNull(claimService, "claimService不能为空");
        this.acceptanceService = Objects.requireNonNull(acceptanceService, "acceptanceService不能为空");
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
        // claim 认领 Run
        CoachingRunClaimResult claimResult = claimService.claim(
                sessionId,
                requestId,
                expectedSessionVersion,
                message
        );
        CoachingRun claimed = claimResult.run();

        requireOwner(ownerId, claimed);
        if (claimResult.replayed() && claimed.status() != CoachingRunStatus.RECEIVED) return claimed;

        // 衔接 Run 容量准入，申请 Semaphore
        // 保护下游模型和工具，防止容量已满
        RunAdmissionLease lease = dispatcher.acquire(ownerId);
        boolean handedOff = false;

        try {
            // acceptance 接受 run
            CoachingRun accepted = acceptanceService.accept(claimed.runId(), message);
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

            // 提交虚拟线程
            dispatcher.dispatch(context, lease, asyncTask::execute);
            handedOff = true;
            return accepted;
        } finally {
            if (!handedOff) lease.close();
        }
    }

    private static void requireOwner(ActorId expectedOwner, CoachingRun run) {
        if (!expectedOwner.equals(run.ownerId())) {
            throw new IllegalStateException("Run不属于当前执行用户");
        }
    }
}