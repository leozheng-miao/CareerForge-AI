package com.leo.careerforgeai.interviewflow.application.task;

import com.leo.careerforgeai.interviewflow.config.InterviewTaskExecutorProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskRecoveryTest {

    @Test
    void shouldPollBoundedBatchOnStartupOrSchedule() {
        InterviewTaskWorker worker = mock(InterviewTaskWorker.class);
        InterviewTaskExecutorProperties properties =
                new InterviewTaskExecutorProperties(
                        true,
                        8,
                        8,
                        2,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(90),
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(5)
                );
        when(worker.pollAndDispatch(8)).thenReturn(3);

        InterviewTaskRecovery recovery = new InterviewTaskRecovery(
                worker,
                properties
        );

        assertThat(recovery.recoverAvailableTasks()).isEqualTo(3);
        verify(worker).pollAndDispatch(8);
    }
}
