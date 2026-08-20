package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用短事务启动Run并原子保存ASSISTANT Turn和Run终态
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunLifecycleApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingRunRepository repository;
    private final CoachingSessionApplicationService sessionApplicationService;
    private final Clock clock;

    public CoachingRunLifecycleApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingRunRepository repository,
            CoachingSessionApplicationService sessionApplicationService,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.sessionApplicationService = Objects.requireNonNull(sessionApplicationService, "sessionApplicationService不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public CoachingRunStartResult start(UUID runId) {
        return startForActor(
                currentActorProvider.currentActor(),
                runId
        );
    }

    @Transactional
    public CoachingRunStartResult startForActor(
            ActorId ownerId,
            UUID runId
    ) {
        CoachingRun current = requireOwnedRun(ownerId, runId);

        if (current.status() == CoachingRunStatus.RUNNING
                || current.isTerminal()) {
            return new CoachingRunStartResult(current, false);
        }
        if (current.status() != CoachingRunStatus.ACCEPTED) {
            throw new IllegalStateException("只有ACCEPTED Run可以开始执行");
        }

        CoachingRun running = current.start(clock.instant());
        updateOrThrow(ownerId, running, current.version());
        return new CoachingRunStartResult(running, true);
    }

    @Transactional
    public CoachingRun succeed(
            UUID runId,
            String validatedAnswer,
            String agentRunId
    ) {
        return succeedForActor(
                currentActorProvider.currentActor(),
                runId,
                validatedAnswer,
                agentRunId
        );
    }

    @Transactional
    public CoachingRun succeedForActor(
            ActorId ownerId,
            UUID runId,
            String validatedAnswer,
            String agentRunId
    ) {
        CoachingRun current = requireOwnedRun(ownerId, runId);

        if (current.isTerminal()) return current;
        requireRunning(current);

        ConversationTurn assistantTurn =
                sessionApplicationService
                        .recordValidatedAssistantTurnForActor(
                                ownerId,
                                current.sessionId(),
                                nextSessionVersion(
                                        current.expectedSessionVersion()
                                ),
                                requireUserTurnId(current),
                                validatedAnswer,
                                agentRunId
                        );

        requireAssistantTurnIdentity(
                ownerId,
                current,
                assistantTurn
        );

        CoachingRun succeeded = current.succeed(
                assistantTurn.turnId(),
                clock.instant()
        );
        updateOrThrow(ownerId, succeeded, current.version());
        return succeeded;
    }

    @Transactional
    public CoachingRun fail(
            UUID runId,
            String agentRunId,
            String failureCode
    ) {
        return failForActor(
                currentActorProvider.currentActor(),
                runId,
                agentRunId,
                failureCode
        );
    }

    @Transactional
    public CoachingRun failForActor(
            ActorId ownerId,
            UUID runId,
            String agentRunId,
            String failureCode
    ) {
        return finishFailure(
                ownerId,
                runId,
                agentRunId,
                failureCode,
                false
        );
    }

    @Transactional
    public CoachingRun timeOut(
            UUID runId,
            String agentRunId,
            String failureCode
    ) {
        return timeOutForActor(
                currentActorProvider.currentActor(),
                runId,
                agentRunId,
                failureCode
        );
    }

    @Transactional
    public CoachingRun timeOutForActor(
            ActorId ownerId,
            UUID runId,
            String agentRunId,
            String failureCode
    ) {
        return finishFailure(
                ownerId,
                runId,
                agentRunId,
                failureCode,
                true
        );
    }

    private CoachingRun finishFailure(
            ActorId ownerId,
            UUID runId,
            String agentRunId,
            String failureCode,
            boolean timedOut
    ) {
        CoachingRun current = requireOwnedRun(ownerId, runId);

        if (current.isTerminal()) return current;
        requireRunning(current);

        ConversationTurn assistantTurn =
                sessionApplicationService
                        .recordFailedAssistantTurnForActor(
                                ownerId,
                                current.sessionId(),
                                nextSessionVersion(
                                        current.expectedSessionVersion()
                                ),
                                requireUserTurnId(current),
                                agentRunId,
                                failureCode
                        );

        requireAssistantTurnIdentity(
                ownerId,
                current,
                assistantTurn
        );

        CoachingRun finished = timedOut
                ? current.timeOut(
                assistantTurn.turnId(),
                failureCode,
                clock.instant()
        )
                : current.fail(
                assistantTurn.turnId(),
                failureCode,
                clock.instant()
        );

        updateOrThrow(ownerId, finished, current.version());
        return finished;
    }

    private CoachingRun requireOwnedRun(
            ActorId ownerId,
            UUID runId
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(runId, "runId不能为空");

        return repository.findByRunId(ownerId, runId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Run不存在或不属于当前用户"
                        )
                );
    }

    private static void requireRunning(CoachingRun run) {
        if (run.status() != CoachingRunStatus.RUNNING) {
            throw new IllegalStateException("只有RUNNING Run可以写入执行终态");
        }
    }

    private static UUID requireUserTurnId(CoachingRun run) {
        return Objects.requireNonNull(
                run.userTurnId(),
                "RUNNING Run缺少userTurnId"
        );
    }

    private static long nextSessionVersion(long version) {
        try {
            return Math.incrementExact(version);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Session版本超出允许范围", exception);
        }
    }

    private static void requireAssistantTurnIdentity(
            ActorId ownerId,
            CoachingRun run,
            ConversationTurn assistantTurn
    ) {
        if (!ownerId.equals(assistantTurn.ownerId())
                || !run.sessionId().equals(assistantTurn.sessionId())) {
            throw new IllegalStateException("保存的ASSISTANT Turn与Run身份不一致");
        }
    }

    private void updateOrThrow(
            ActorId ownerId,
            CoachingRun updated,
            long expectedVersion
    ) {
        if (!repository.updateIfVersionMatches(
                ownerId,
                updated,
                expectedVersion
        )) {
            throw new CoachingRunVersionConflictException(
                    updated.runId(),
                    expectedVersion
            );
        }
    }
}