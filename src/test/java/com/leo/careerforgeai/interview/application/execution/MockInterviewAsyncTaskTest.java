package com.leo.careerforgeai.interview.application.execution;

import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.interview.application.graph.InterviewGraphExecutionService;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证异步面试异常、取消竞争和Deadline收敛不会覆盖已有终态
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
class MockInterviewAsyncTaskTest {

    private static final ActorId OWNER = new ActorId("async-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("93000000-0000-0000-0000-000000000001");
    private static final UUID ANSWER_ID = UUID.fromString("93000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-30T13:00:00Z");

    @Test
    void shouldKeepCancelledTerminalStateWhenGraphFailsAfterCancellation() {
        InterviewGraphExecutionService graphService = mock(InterviewGraphExecutionService.class);
        MockInterviewLifecycleApplicationService lifecycleService = mock(MockInterviewLifecycleApplicationService.class);
        MockInterviewSession cancelled = mock(MockInterviewSession.class);
        RuntimeException graphFailure = new IllegalStateException("取消后Graph节点CAS失败");

        when(cancelled.isTerminal()).thenReturn(true);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(cancelled);
        when(graphService.start(INTERVIEW_ID)).thenThrow(graphFailure);

        MockInterviewAsyncTask task = new MockInterviewAsyncTask(
                graphService, lifecycleService, Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> task.start(context(NOW.plusSeconds(30)))).isSameAs(graphFailure);
        verify(lifecycleService, never()).interrupt(any(), anyLong(), any());
    }

    @Test
    void shouldInterruptActiveInterviewOnceWhenGraphFails() {
        InterviewGraphExecutionService graphService = mock(InterviewGraphExecutionService.class);
        MockInterviewLifecycleApplicationService lifecycleService = mock(MockInterviewLifecycleApplicationService.class);
        MockInterviewSession active = mock(MockInterviewSession.class);
        RuntimeException graphFailure = new IllegalStateException("评审节点失败");

        when(active.isTerminal()).thenReturn(false);
        when(active.interviewId()).thenReturn(INTERVIEW_ID);
        when(active.version()).thenReturn(6L);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(active);
        when(graphService.resumeAfterAnswer(INTERVIEW_ID, ANSWER_ID)).thenThrow(graphFailure);

        MockInterviewAsyncTask task = new MockInterviewAsyncTask(
                graphService, lifecycleService, Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> task.resumeAfterAnswer(
                context(NOW.plusSeconds(30)), ANSWER_ID
        )).isSameAs(graphFailure);

        verify(lifecycleService).interrupt(
                INTERVIEW_ID, 6, InterviewFailureCode.INTERNAL_ERROR
        );
    }

    @Test
    void shouldConvergeExpiredExecutionToDeadlineFailure() {
        InterviewGraphExecutionService graphService = mock(InterviewGraphExecutionService.class);
        MockInterviewLifecycleApplicationService lifecycleService = mock(MockInterviewLifecycleApplicationService.class);
        MockInterviewSession active = mock(MockInterviewSession.class);

        when(active.isTerminal()).thenReturn(false);
        when(active.interviewId()).thenReturn(INTERVIEW_ID);
        when(active.version()).thenReturn(7L);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(active);

        MockInterviewAsyncTask task = new MockInterviewAsyncTask(
                graphService,
                lifecycleService,
                Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC)
        );
        task.recover(context(NOW.plusSeconds(30)));

        verify(lifecycleService).interrupt(
                INTERVIEW_ID, 7, InterviewFailureCode.EXECUTION_DEADLINE_EXCEEDED
        );
    }

    private RunExecutionContext context(Instant deadline) {
        return new RunExecutionContext(
                OWNER,
                INTERVIEW_ID,
                "interview-" + INTERVIEW_ID,
                NOW,
                deadline
        );
    }
}