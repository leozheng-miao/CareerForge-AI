package com.leo.careerforgeai.agent.application.coach.conversation;

import com.leo.careerforgeai.agent.application.coach.CareerCoachExecutionException;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.agent.application.coach.CareerCoachResult;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
import com.leo.careerforgeai.memory.application.context.ConversationContext;
import com.leo.careerforgeai.memory.application.context.ConversationContextAssembler;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
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
 * @description: 串联用户Turn、结构化Context、Career Coach调用和助手Turn持久化
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
@Service
@Slf4j
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public final class ConversationalCareerCoachApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingSessionApplicationService sessionApplicationService;
    private final MemoryRepository memoryRepository;
    private final ConversationContextAssembler contextAssembler;
    private final CareerCoachService careerCoachService;

    public ConversationalCareerCoachApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingSessionApplicationService sessionApplicationService,
            MemoryRepository memoryRepository,
            ConversationContextAssembler contextAssembler,
            CareerCoachService careerCoachService
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionApplicationService = Objects.requireNonNull(sessionApplicationService, "sessionApplicationService不能为空");
        this.memoryRepository = Objects.requireNonNull(memoryRepository, "memoryRepository不能为空");
        this.contextAssembler = Objects.requireNonNull(contextAssembler, "contextAssembler不能为空");
        this.careerCoachService = Objects.requireNonNull(careerCoachService, "careerCoachService不能为空");
    }

    /**
     * 保存当前用户消息，组装受控Context，调用Agent并保存可信助手回答。
     * 该方法不能添加覆盖整个流程的事务，因为模型调用不应占用数据库长事务。
     */
    public ConversationalCareerCoachResult coach(
            UUID sessionId,
            long expectedSessionVersion,
            String message
    ) {
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        if (expectedSessionVersion < 0) {
            throw new IllegalArgumentException("expectedSessionVersion不能小于0");
        }

        long versionAfterUserTurn = nextVersion(expectedSessionVersion);
        long versionAfterAssistantTurn = nextVersion(versionAfterUserTurn);

        ActorId actorId = currentActorProvider.currentActor();

        ConversationTurn userTurn = sessionApplicationService.recordUserTurn(
                sessionId,
                expectedSessionVersion,
                message
        );

        if (!userTurn.ownerId().equals(actorId)) {
            throw new IllegalStateException("保存后的用户Turn归属异常");
        }

        List<ConversationTurn> recentTurns =
                sessionApplicationService.getRecentTurns(sessionId);

        List<MemoryItem> confirmedMemories =
                memoryRepository.findConfirmedByOwner(actorId);

        ConversationContext context = contextAssembler.assemble(
                userTurn,
                recentTurns,
                confirmedMemories
        );

        try {
            CareerCoachResult coachResult =
                    careerCoachService.coachWithContext(context);

            sessionApplicationService.recordValidatedAssistantTurn(
                    sessionId,
                    versionAfterUserTurn,
                    userTurn.turnId(),
                    coachResult.answer().answer(),
                    coachResult.trace().runId()
            );

            return new ConversationalCareerCoachResult(
                    userTurn.sessionId(),
                    versionAfterAssistantTurn,
                    coachResult
            );
        } catch (CareerCoachExecutionException exception) {
            recordFailureWithoutMasking(
                    sessionId,
                    versionAfterUserTurn,
                    userTurn,
                    exception.getTrace().runId(),
                    exception.getTerminationReason().name(),
                    exception
            );
            throw exception;
        } catch (CareerCoachFinalAnswerException exception) {
            if (exception.getTrace() != null) {
                recordFailureWithoutMasking(
                        sessionId,
                        versionAfterUserTurn,
                        userTurn,
                        exception.getTrace().runId(),
                        exception.getErrorType().name(),
                        exception
                );
            } else {
                log.error(
                        "Career Coach最终回答校验失败但缺少Trace，sessionId={}, errorType={}",
                        sessionId,
                        exception.getErrorType()
                );
            }
            throw exception;
        }
    }

    /**
     * 尝试保存受控失败Turn。
     * 失败记录写入异常不能覆盖原始Agent异常，而是作为suppressed异常保留。
     */
    private void recordFailureWithoutMasking(
            UUID sessionId,
            long expectedSessionVersion,
            ConversationTurn userTurn,
            String agentRunId,
            String failureCode,
            RuntimeException originalException
    ) {
        try {
            sessionApplicationService.recordFailedAssistantTurn(
                    sessionId,
                    expectedSessionVersion,
                    userTurn.turnId(),
                    agentRunId,
                    failureCode
            );
        } catch (RuntimeException persistenceException) {
            originalException.addSuppressed(persistenceException);
            log.warn(
                    "Career Coach失败Turn保存失败，sessionId={}, failureCode={}, persistenceError={}",
                    sessionId,
                    failureCode,
                    persistenceException.getClass().getSimpleName()
            );
        }
    }

    /** 安全计算下一Session版本，拒绝long溢出。 */
    private static long nextVersion(long currentVersion) {
        try {
            return Math.incrementExact(currentVersion);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Session版本超出允许范围", exception);
        }
    }
}