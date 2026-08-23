package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.agent.application.coach.CareerCoachExecutionException;
import com.leo.careerforgeai.agent.application.coach.CareerCoachResult;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.agent.application.loop.AgentLoopObserver;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunProgressEventPublisher;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunLifecycleApplicationService;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunStartResult;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.memory.application.context.ConversationContext;
import com.leo.careerforgeai.memory.application.context.ConversationContextAssembler;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用显式owner在事务外组装Context并执行现有Career Coach调用链
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Service
@Slf4j
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunExecutionApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingConversationRepository conversationRepository;
    private final CoachingSessionApplicationService sessionApplicationService;
    private final MemoryRepository memoryRepository;
    private final ConversationContextAssembler contextAssembler;
    private final CareerCoachService careerCoachService;
    private final CoachingRunLifecycleApplicationService lifecycleService;
    private final CoachingRunProgressEventPublisher progressEventPublisher;

    public CoachingRunExecutionApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingConversationRepository conversationRepository,
            CoachingSessionApplicationService sessionApplicationService,
            MemoryRepository memoryRepository,
            ConversationContextAssembler contextAssembler,
            CareerCoachService careerCoachService,
            CoachingRunLifecycleApplicationService lifecycleService,
            CoachingRunProgressEventPublisher progressEventPublisher
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.conversationRepository = Objects.requireNonNull(conversationRepository, "conversationRepository不能为空");
        this.sessionApplicationService = Objects.requireNonNull(sessionApplicationService, "sessionApplicationService不能为空");
        this.memoryRepository = Objects.requireNonNull(memoryRepository, "memoryRepository不能为空");
        this.contextAssembler = Objects.requireNonNull(contextAssembler, "contextAssembler不能为空");
        this.careerCoachService = Objects.requireNonNull(careerCoachService, "careerCoachService不能为空");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService不能为空");
        this.progressEventPublisher = Objects.requireNonNull(progressEventPublisher, "progressEventPublisher不能为空");
    }

    public CoachingRun execute(UUID runId) {
        return execute(
                currentActorProvider.currentActor(),
                runId
        );
    }

    public CoachingRun execute(RunExecutionContext executionContext) {
        Objects.requireNonNull(executionContext, "executionContext不能为空");
        return execute(
                executionContext.ownerId(),
                executionContext.runId()
        );
    }

    private CoachingRun execute(
            ActorId ownerId,
            UUID runId
    ) {
        CoachingRunStartResult startResult =
                lifecycleService.startForActor(
                        ownerId,
                        runId
                );

        if (!startResult.started()) return startResult.run();

        CoachingRun running = startResult.run();
        ConversationTurn userTurn =
                requireUserTurn(ownerId, running);

        List<ConversationTurn> recentTurns =
                sessionApplicationService
                        .getRecentTurnsForActor(
                                ownerId,
                                running.sessionId()
                        );

        List<MemoryItem> confirmedMemories =
                memoryRepository.findConfirmedByOwner(ownerId);

        ConversationContext context = contextAssembler.assemble(
                userTurn,
                recentTurns,
                confirmedMemories
        );

        AgentLoopObserver observer = progressEventPublisher.observerFor(
                ownerId,
                running.runId()
        );

        try {
            CareerCoachResult result =
                    careerCoachService.coachWithContext(context, observer);

            return lifecycleService.succeedForActor(
                    ownerId,
                    running.runId(),
                    result.answer().answer(),
                    result.trace().runId()
            );
        } catch (CareerCoachExecutionException exception) {
            finishExecutionFailureWithoutMasking(
                    ownerId,
                    running.runId(),
                    exception
            );
            throw exception;
        } catch (CareerCoachFinalAnswerException exception) {
            finishValidationFailureWithoutMasking(
                    ownerId,
                    running.runId(),
                    exception
            );
            throw exception;
        }
    }

    private ConversationTurn requireUserTurn(
            ActorId ownerId,
            CoachingRun run
    ) {
        UUID userTurnId = Objects.requireNonNull(
                run.userTurnId(),
                "RUNNING Run缺少userTurnId"
        );

        ConversationTurn userTurn =
                conversationRepository
                        .findTurn(ownerId, userTurnId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Run关联的USER Turn不存在"
                                )
                        );

        if (!run.sessionId().equals(userTurn.sessionId())) {
            throw new IllegalStateException("Run关联的USER Turn不属于当前Session");
        }
        return userTurn;
    }

    private void finishExecutionFailureWithoutMasking(
            ActorId ownerId,
            UUID runId,
            CareerCoachExecutionException original
    ) {
        try {
            if (original.getRunStatus() == AgentRunStatus.TIMED_OUT) {
                lifecycleService.timeOutForActor(
                        ownerId,
                        runId,
                        original.getTrace().runId(),
                        original.getTerminationReason().name()
                );
            } else {
                lifecycleService.failForActor(
                        ownerId,
                        runId,
                        original.getTrace().runId(),
                        original.getTerminationReason().name()
                );
            }
        } catch (RuntimeException persistenceException) {
            original.addSuppressed(persistenceException);
            log.warn(
                    "Run失败终态保存失败，runId={}, persistenceError={}",
                    runId,
                    persistenceException.getClass().getSimpleName()
            );
        }
    }

    private void finishValidationFailureWithoutMasking(
            ActorId ownerId,
            UUID runId,
            CareerCoachFinalAnswerException original
    ) {
        if (original.getTrace() == null) {
            log.error("最终回答校验失败但缺少真实Agent Trace，runId={}", runId);
            return;
        }

        try {
            lifecycleService.failForActor(
                    ownerId,
                    runId,
                    original.getTrace().runId(),
                    original.getErrorType().name()
            );
        } catch (RuntimeException persistenceException) {
            original.addSuppressed(persistenceException);
            log.warn(
                    "Run校验失败终态保存失败，runId={}, persistenceError={}",
                    runId,
                    persistenceException.getClass().getSimpleName()
            );
        }
    }
}