package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.coach.CareerCoachExecutionException;
import com.leo.careerforgeai.agent.application.coach.CareerCoachResult;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerErrorType;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunExecutionApplicationService;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunLifecycleApplicationService;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunStartResult;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.memory.application.context.ConversationContext;
import com.leo.careerforgeai.memory.application.context.ConversationContextAssembler;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Run执行、幂等重放、成功终结、超时、回答校验失败和显式owner传递
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunExecutionApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_TURN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID EXCHANGE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T05:00:00Z");
    private static final String MESSAGE = "请解释Java并发";

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private CoachingConversationRepository conversationRepository;

    @Mock
    private CoachingSessionApplicationService sessionApplicationService;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private ConversationContextAssembler contextAssembler;

    @Mock
    private CareerCoachService careerCoachService;

    @Mock
    private CoachingRunLifecycleApplicationService lifecycleService;

    private CoachingRunExecutionApplicationService service;
    private ConversationTurn userTurn;
    private ConversationContext context;

    @BeforeEach
    void setUp() {
        service = new CoachingRunExecutionApplicationService(
                currentActorProvider,
                conversationRepository,
                sessionApplicationService,
                memoryRepository,
                contextAssembler,
                careerCoachService,
                lifecycleService
        );

        userTurn = ConversationTurn.completedUser(
                USER_TURN_ID,
                SESSION_ID,
                EXCHANGE_ID,
                OWNER,
                5L,
                MESSAGE,
                NOW.minusSeconds(20)
        );

        context = new ConversationContext(
                SESSION_ID,
                List.of(),
                List.of(),
                MESSAGE,
                new ConversationContext.ContextUsage(
                        0,
                        1,
                        0,
                        MESSAGE.length(),
                        (MESSAGE.length() + 1) / 2,
                        false,
                        false
                )
        );
    }

    @Test
    void shouldExecuteCoachAndSucceedRun() {
        CoachingRun running = runningRun();
        CoachingRun succeeded = running.succeed(ASSISTANT_TURN_ID, NOW);

        prepareRunningExecution(running);
        when(careerCoachService.coachWithContext(context)).thenReturn(completedCoachResult());
        when(lifecycleService.succeedForActor(
                OWNER,
                RUN_ID,
                "可信回答",
                "agent-run-success"
        )).thenReturn(succeeded);

        CoachingRun result = service.execute(RUN_ID);

        assertThat(result).isSameAs(succeeded);
        verify(lifecycleService).succeedForActor(
                OWNER,
                RUN_ID,
                "可信回答",
                "agent-run-success"
        );
    }

    @Test
    void shouldReplayExistingRunWithoutCallingModel() {
        CoachingRun running = runningRun();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(lifecycleService.startForActor(OWNER, RUN_ID))
                .thenReturn(new CoachingRunStartResult(running, false));

        CoachingRun result = service.execute(RUN_ID);

        assertThat(result).isSameAs(running);
        verifyNoInteractions(
                conversationRepository,
                sessionApplicationService,
                memoryRepository,
                contextAssembler,
                careerCoachService
        );
    }

    @Test
    void shouldPersistTimedOutRunAndKeepOriginalException() {
        CoachingRun running = runningRun();
        CareerCoachExecutionException failure = timeoutException();

        prepareRunningExecution(running);
        when(careerCoachService.coachWithContext(context)).thenThrow(failure);

        assertThatThrownBy(() -> service.execute(RUN_ID)).isSameAs(failure);

        verify(lifecycleService).timeOutForActor(
                OWNER,
                RUN_ID,
                "agent-run-timeout",
                "MODEL_TIMEOUT"
        );
    }

    @Test
    void shouldPersistFailedRunWhenFinalAnswerValidationFails() {
        CoachingRun running = runningRun();
        AgentRunTrace trace = trace(
                "agent-run-invalid",
                AgentRunStatus.COMPLETED,
                AgentTerminationReason.FINAL_ANSWER
        );
        CareerCoachFinalAnswerException failure = new CareerCoachFinalAnswerException(
                CareerCoachFinalAnswerErrorType.CITATION_NOT_ALLOWED,
                "最终回答包含未授权引用"
        ).withTrace(trace);

        prepareRunningExecution(running);
        when(careerCoachService.coachWithContext(context)).thenThrow(failure);

        assertThatThrownBy(() -> service.execute(RUN_ID)).isSameAs(failure);

        verify(lifecycleService).failForActor(
                OWNER,
                RUN_ID,
                "agent-run-invalid",
                "CITATION_NOT_ALLOWED"
        );
    }

    @Test
    void shouldUseExplicitOwnerFromRunExecutionContext() {
        CoachingRun running = runningRun();
        CoachingRun succeeded = running.succeed(ASSISTANT_TURN_ID, NOW);
        RunExecutionContext executionContext = new RunExecutionContext(
                OWNER,
                RUN_ID,
                "trace-explicit-owner",
                NOW.minusSeconds(30),
                NOW.plusSeconds(30)
        );

        prepareExplicitOwnerExecution(running);
        when(careerCoachService.coachWithContext(context)).thenReturn(completedCoachResult());
        when(lifecycleService.succeedForActor(
                OWNER,
                RUN_ID,
                "可信回答",
                "agent-run-success"
        )).thenReturn(succeeded);

        CoachingRun result = service.execute(executionContext);

        assertThat(result).isSameAs(succeeded);
        verifyNoInteractions(currentActorProvider);
    }

    private void prepareRunningExecution(CoachingRun running) {
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        prepareExplicitOwnerExecution(running);
    }

    private void prepareExplicitOwnerExecution(CoachingRun running) {
        when(lifecycleService.startForActor(OWNER, RUN_ID))
                .thenReturn(new CoachingRunStartResult(running, true));
        when(conversationRepository.findTurn(OWNER, USER_TURN_ID))
                .thenReturn(Optional.of(userTurn));
        when(sessionApplicationService.getRecentTurnsForActor(OWNER, SESSION_ID))
                .thenReturn(List.of(userTurn));
        when(memoryRepository.findConfirmedByOwner(OWNER)).thenReturn(List.of());
        when(contextAssembler.assemble(userTurn, List.of(userTurn), List.of()))
                .thenReturn(context);
    }

    private CareerCoachResult completedCoachResult() {
        return new CareerCoachResult(
                new CareerCoachAnswer(
                        CareerCoachAnswerStatus.ANSWERED,
                        "可信回答",
                        List.of()
                ),
                trace(
                        "agent-run-success",
                        AgentRunStatus.COMPLETED,
                        AgentTerminationReason.FINAL_ANSWER
                )
        );
    }

    private CareerCoachExecutionException timeoutException() {
        AgentRunTrace trace = trace(
                "agent-run-timeout",
                AgentRunStatus.TIMED_OUT,
                AgentTerminationReason.MODEL_TIMEOUT
        );
        AgentLoopResult result = AgentLoopResult.terminated(
                AgentRunStatus.TIMED_OUT,
                AgentTerminationReason.MODEL_TIMEOUT,
                trace,
                List.of()
        );
        return new CareerCoachExecutionException(result);
    }

    private CoachingRun runningRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                "a".repeat(64),
                4L,
                NOW.minusSeconds(30)
        ).accept(
                USER_TURN_ID,
                NOW.minusSeconds(20)
        ).start(NOW.minusSeconds(10));
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