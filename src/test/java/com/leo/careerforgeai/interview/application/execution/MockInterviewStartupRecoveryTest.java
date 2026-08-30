package com.leo.careerforgeai.interview.application.execution;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * @program: CareerForge-AI
 * @description: 验证应用启动时仅将执行中面试通过受控异步边界恢复一次
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
class MockInterviewStartupRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-08-30T07:00:00Z");
    private static final ActorId OWNER = new ActorId("startup-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Test
    void shouldDispatchExecutionRequiredInterviewOnce() {
        MockInterviewSessionRepository repository = mock(MockInterviewSessionRepository.class);
        CoachingRunAsyncDispatcher dispatcher = mock(CoachingRunAsyncDispatcher.class);
        MockInterviewAsyncTask asyncTask = mock(MockInterviewAsyncTask.class);
        MockInterviewSession candidate = mock(MockInterviewSession.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        CoachingRunExecutionProperties properties = new CoachingRunExecutionProperties(
                4, 1, Duration.ofSeconds(30), Duration.ofSeconds(5)
        );

        when(candidate.ownerId()).thenReturn(OWNER);
        when(candidate.interviewId()).thenReturn(INTERVIEW_ID);
        when(repository.findExecutionRequiredUpdatedBefore(OWNER, NOW, 100))
                .thenReturn(List.of(candidate), List.of());
        when(dispatcher.dispatch(
                any(RunExecutionContext.class),
                org.mockito.ArgumentMatchers.<Consumer<RunExecutionContext>>any()
        )).thenAnswer(invocation -> {
            RunExecutionContext context = invocation.getArgument(0);
            Consumer<RunExecutionContext> task = invocation.getArgument(1);
            task.accept(context);
            return CompletableFuture.completedFuture(null);
        });

        MockInterviewStartupRecovery recovery = new MockInterviewStartupRecovery(
                () -> OWNER, repository, dispatcher, asyncTask, properties, clock
        );
        recovery.recoverExecutionRequiredInterviews();

        verify(asyncTask).recover(org.mockito.ArgumentMatchers.argThat(context ->
                context.ownerId().equals(OWNER)
                        && context.runId().equals(INTERVIEW_ID)
                        && context.submittedAt().equals(NOW)
                        && context.deadline().equals(NOW.plusSeconds(30))
        ));
        verify(repository, times(2)).findExecutionRequiredUpdatedBefore(OWNER, NOW, 100);    }
}