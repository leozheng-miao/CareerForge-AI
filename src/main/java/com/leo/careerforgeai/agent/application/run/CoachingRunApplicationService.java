package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunExecutionApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAcceptanceApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimResult;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 编排Run认领、接受、同步执行、幂等重放和owner隔离查询
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingRunRepository repository;
    private final CoachingRunClaimApplicationService claimService;
    private final CoachingRunAcceptanceApplicationService acceptanceService;
    private final CoachingRunExecutionApplicationService executionService;

    public CoachingRunApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingRunRepository repository,
            CoachingRunClaimApplicationService claimService,
            CoachingRunAcceptanceApplicationService acceptanceService,
            CoachingRunExecutionApplicationService executionService
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.claimService = Objects.requireNonNull(claimService, "claimService不能为空");
        this.acceptanceService = Objects.requireNonNull(acceptanceService, "acceptanceService不能为空");
        this.executionService = Objects.requireNonNull(executionService, "executionService不能为空");
    }

    public CoachingRun submit(
            UUID sessionId,
            UUID requestId,
            long expectedSessionVersion,
            String message
    ) {
        // 调用Claim Service 创建 任务单
        CoachingRunClaimResult claimResult = claimService.claim(
                sessionId,
                requestId,
                expectedSessionVersion,
                message
        );
        // 如果已经有重复 runId， 直接返回现有的run
        if (claimResult.replayed()) return claimResult.run();

        CoachingRun accepted = acceptanceService.accept(claimResult.run().runId(), message);
        return executionService.execute(accepted.runId());
    }

    @Transactional(readOnly = true)
    public CoachingRun get(UUID runId) {
        Objects.requireNonNull(runId, "runId不能为空");
        ActorId ownerId = currentActorProvider.currentActor();
        return repository.findByRunId(ownerId, runId)
                .orElseThrow(() -> new CoachingRunNotFoundException(runId));
    }
}