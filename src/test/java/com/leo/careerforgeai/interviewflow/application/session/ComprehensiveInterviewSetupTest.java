package com.leo.careerforgeai.interviewflow.application.session;

import com.leo.careerforgeai.interviewflow.application.port.*;
import com.leo.careerforgeai.interviewflow.domain.session.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ComprehensiveInterviewSetupTest {
    private static final ActorId OWNER = new ActorId("comprehensive-owner");
    private static final UUID ID = UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-09T02:00:00Z");

    @Test
    void validatesRequiredInputsDurationAndDifficulty() {
        assertThatThrownBy(() -> command("", 3600)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("JD", 1800)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreateComprehensiveInterviewCommand(UUID.randomUUID(),
                "AI_AGENT_DEVELOPMENT", "JD", "30k-47k", null, null,
                3600, InterviewDifficulty.STANDARD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salaryRange");
        assertThat(command("JD", 5400).difficulty()).isEqualTo(InterviewDifficulty.CHALLENGE);
        assertThat(command("JD", 5400).salaryRange()).isEqualTo("30K-40K/月");
    }

    @Test
    void updateIncrementsVersionsAndEnqueuesOnlyNewPreparation() {
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewInputSnapshotRepository inputs = mock(InterviewInputSnapshotRepository.class);
        InterviewTaskRepository tasks = mock(InterviewTaskRepository.class);
        InterviewAggregate before = InterviewAggregate.createComprehensive(
                ID, OWNER, NOW, new ComprehensiveInterviewPolicy(3600));
        InterviewAggregate after = InterviewAggregate.createComprehensive(
                ID, OWNER, NOW, new ComprehensiveInterviewPolicy(5400));
        when(sessions.findById(OWNER, ID)).thenReturn(Optional.of(before), Optional.of(after));
        when(sessions.findVersions(OWNER, ID)).thenReturn(Optional.of(
                new InterviewSessionRepository.SessionVersions(1, 1)));
        var snapshot = new InterviewInputSnapshotRepository.InputSnapshot(UUID.randomUUID(), ID,
                OWNER, "AI_AGENT_DEVELOPMENT", "old JD", "20K-30K/月", null, null,
                null, "STANDARD", 3600, 1, 0, NOW, NOW);
        when(inputs.findBySession(OWNER, ID)).thenReturn(Optional.of(snapshot));
        when(inputs.updateIfVersionMatches(eq(OWNER), any(), eq(0L))).thenReturn(true);
        when(sessions.reconfigureBeforeStart(OWNER, ID, 0, 2, 2, 5400)).thenReturn(true);
        when(tasks.enqueue(any())).thenReturn(true);
        var service = new InterviewSessionApplicationService(() -> OWNER, sessions,
                inputs, tasks, Clock.fixed(NOW, ZoneOffset.UTC), JsonMapper.builder().build());

        service.updateSetup(new UpdateInterviewSetupCommand(ID, "new JD", "30K-40K/月",
                null, null, 5400, InterviewDifficulty.CHALLENGE, 0));

        verify(inputs).updateIfVersionMatches(eq(OWNER), argThat(value ->
                value.inputVersion() == 2 && value.durationSeconds() == 5400), eq(0L));
        verify(tasks).enqueue(argThat(task -> task.inputVersion() == 2
                && task.planVersion() == 2));
    }

    private static CreateComprehensiveInterviewCommand command(String jd, long duration) {
        return new CreateComprehensiveInterviewCommand(UUID.randomUUID(),
                "AI_AGENT_DEVELOPMENT", jd, "30K-40K/月", null, null,
                duration, InterviewDifficulty.CHALLENGE);
    }
}
