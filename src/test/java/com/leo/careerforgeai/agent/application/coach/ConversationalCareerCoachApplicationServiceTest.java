package com.leo.careerforgeai.agent.application.coach;

import com.leo.careerforgeai.agent.application.coach.conversation.ConversationalCareerCoachApplicationService;
import com.leo.careerforgeai.agent.application.coach.conversation.ConversationalCareerCoachResult;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerErrorType;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.memory.application.context.ConversationContext;
import com.leo.careerforgeai.memory.application.context.ConversationContextAssembler;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @program: CareerForge-AI
 * @description: 验证会话式Career Coach的保存、Context组装及成功失败编排
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
@ExtendWith(MockitoExtension.class)
class ConversationalCareerCoachApplicationServiceTest {

    private static final ActorId ACTOR_ID = new ActorId("actor-a");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EXCHANGE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final long EXPECTED_VERSION = 4;
    private static final long VERSION_AFTER_USER = 5;
    private static final String USER_MESSAGE = "请继续解释乐观锁";

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private CoachingSessionApplicationService sessionApplicationService;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private ConversationContextAssembler contextAssembler;

    @Mock
    private CareerCoachService careerCoachService;

    private ConversationalCareerCoachApplicationService service;
    private ConversationTurn userTurn;
    private ConversationContext context;

    @BeforeEach
    void setUp() {
        service = new ConversationalCareerCoachApplicationService(
                currentActorProvider,
                sessionApplicationService,
                memoryRepository,
                contextAssembler,
                careerCoachService
        );

        userTurn = ConversationTurn.completedUser(
                USER_TURN_ID,
                SESSION_ID,
                EXCHANGE_ID,
                ACTOR_ID,
                5,
                USER_MESSAGE,
                NOW
        );

        context = new ConversationContext(
                SESSION_ID,
                List.of(),
                List.of(),
                USER_MESSAGE,
                new ConversationContext.ContextUsage(
                        0,
                        1,
                        0,
                        USER_MESSAGE.length(),
                        (USER_MESSAGE.length() + 1) / 2,
                        false,
                        false
                )
        );
    }

    @Test
    void shouldSaveUserCallCoachAndSaveValidatedAssistantInOrder() {
        CareerCoachResult coachResult = completedCoachResult("run-success");

        prepareContext();
        when(careerCoachService.coachWithContext(context)).thenReturn(coachResult);

        ConversationalCareerCoachResult result =
                service.coach(SESSION_ID, EXPECTED_VERSION, USER_MESSAGE);

        assertThat(result.sessionId()).isEqualTo(SESSION_ID);
        assertThat(result.sessionVersion()).isEqualTo(6);
        assertThat(result.coachResult()).isSameAs(coachResult);

        InOrder order = inOrder(
                currentActorProvider,
                sessionApplicationService,
                memoryRepository,
                contextAssembler,
                careerCoachService
        );

        order.verify(currentActorProvider).currentActor();
        order.verify(sessionApplicationService)
                .recordUserTurn(SESSION_ID, EXPECTED_VERSION, USER_MESSAGE);
        order.verify(sessionApplicationService).getRecentTurns(SESSION_ID);
        order.verify(memoryRepository).findConfirmedByOwner(ACTOR_ID);
        order.verify(contextAssembler)
                .assemble(userTurn, List.of(userTurn), List.of());
        order.verify(careerCoachService).coachWithContext(context);
        order.verify(sessionApplicationService).recordValidatedAssistantTurn(
                SESSION_ID,
                VERSION_AFTER_USER,
                USER_TURN_ID,
                coachResult.answer().answer(),
                "run-success"
        );

        verify(sessionApplicationService, never()).recordFailedAssistantTurn(
                any(UUID.class),
                anyLong(),
                any(UUID.class),
                anyString(),
                anyString()
        );
    }

    @Test
    void shouldSaveControlledFailureWhenAgentDoesNotComplete() {
        prepareContext();

        AgentLoopResult timedOut = AgentLoopResult.terminated(
                AgentRunStatus.TIMED_OUT,
                AgentTerminationReason.MODEL_TIMEOUT,
                trace(
                        "run-timeout",
                        AgentRunStatus.TIMED_OUT,
                        AgentTerminationReason.MODEL_TIMEOUT
                ),
                List.of()
        );

        CareerCoachExecutionException failure =
                new CareerCoachExecutionException(timedOut);

        when(careerCoachService.coachWithContext(context)).thenThrow(failure);

        assertThatThrownBy(
                () -> service.coach(SESSION_ID, EXPECTED_VERSION, USER_MESSAGE)
        ).isSameAs(failure);

        verify(sessionApplicationService).recordFailedAssistantTurn(
                SESSION_ID,
                VERSION_AFTER_USER,
                USER_TURN_ID,
                "run-timeout",
                "MODEL_TIMEOUT"
        );

        verify(sessionApplicationService, never()).recordValidatedAssistantTurn(
                any(UUID.class),
                anyLong(),
                any(UUID.class),
                anyString(),
                anyString()
        );
    }

    @Test
    void shouldSaveControlledFailureWhenFinalAnswerValidationFails() {
        prepareContext();

        AgentRunTrace trace = trace(
                "run-invalid-answer",
                AgentRunStatus.COMPLETED,
                AgentTerminationReason.FINAL_ANSWER
        );

        CareerCoachFinalAnswerException failure =
                new CareerCoachFinalAnswerException(
                        CareerCoachFinalAnswerErrorType.CITATION_NOT_ALLOWED,
                        "最终回答包含未经授权的引用"
                ).withTrace(trace);

        when(careerCoachService.coachWithContext(context)).thenThrow(failure);

        assertThatThrownBy(
                () -> service.coach(SESSION_ID, EXPECTED_VERSION, USER_MESSAGE)
        ).isSameAs(failure);

        verify(sessionApplicationService).recordFailedAssistantTurn(
                SESSION_ID,
                VERSION_AFTER_USER,
                USER_TURN_ID,
                "run-invalid-answer",
                "CITATION_NOT_ALLOWED"
        );

        verify(sessionApplicationService, never()).recordValidatedAssistantTurn(
                any(UUID.class),
                anyLong(),
                any(UUID.class),
                anyString(),
                anyString()
        );
    }

    @Test
    void shouldKeepOriginalAgentFailureWhenFailureTurnCannotBeSaved() {
        prepareContext();

        AgentLoopResult timedOut = AgentLoopResult.terminated(
                AgentRunStatus.TIMED_OUT,
                AgentTerminationReason.MODEL_TIMEOUT,
                trace(
                        "run-timeout",
                        AgentRunStatus.TIMED_OUT,
                        AgentTerminationReason.MODEL_TIMEOUT
                ),
                List.of()
        );

        CareerCoachExecutionException originalFailure =
                new CareerCoachExecutionException(timedOut);

        IllegalStateException persistenceFailure =
                new IllegalStateException("Session并发更新冲突");

        when(careerCoachService.coachWithContext(context))
                .thenThrow(originalFailure);

        when(sessionApplicationService.recordFailedAssistantTurn(
                SESSION_ID,
                VERSION_AFTER_USER,
                USER_TURN_ID,
                "run-timeout",
                "MODEL_TIMEOUT"
        )).thenThrow(persistenceFailure);

        Throwable thrown = catchThrowable(
                () -> service.coach(SESSION_ID, EXPECTED_VERSION, USER_MESSAGE)
        );

        assertThat(thrown).isSameAs(originalFailure);
        assertThat(thrown.getSuppressed()).containsExactly(persistenceFailure);
    }

    private void prepareContext() {
        when(currentActorProvider.currentActor()).thenReturn(ACTOR_ID);
        when(sessionApplicationService.recordUserTurn(
                SESSION_ID,
                EXPECTED_VERSION,
                USER_MESSAGE
        )).thenReturn(userTurn);
        when(sessionApplicationService.getRecentTurns(SESSION_ID))
                .thenReturn(List.of(userTurn));
        when(memoryRepository.findConfirmedByOwner(ACTOR_ID))
                .thenReturn(List.of());
        when(contextAssembler.assemble(
                userTurn,
                List.of(userTurn),
                List.of()
        )).thenReturn(context);
    }

    private CareerCoachResult completedCoachResult(String runId) {
        CareerCoachAnswer answer = new CareerCoachAnswer(
                CareerCoachAnswerStatus.ANSWERED,
                "乐观锁通过版本号检测并发更新。",
                List.of()
        );

        return new CareerCoachResult(
                answer,
                trace(
                        runId,
                        AgentRunStatus.COMPLETED,
                        AgentTerminationReason.FINAL_ANSWER
                )
        );
    }

    private AgentRunTrace trace(
            String runId,
            AgentRunStatus status,
            AgentTerminationReason terminationReason
    ) {
        return new AgentRunTrace(
                runId,
                NOW,
                NOW,
                status,
                terminationReason,
                List.of(),
                List.of()
        );
    }
}