package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository.InputSnapshot;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort;
import com.leo.careerforgeai.interviewflow.config.InterviewPreparationExecutor;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComprehensivePreparationCoordinatorTest {
    @Test
    void optionalKnowledgeFailureFallsBackWithoutLosingRequiredJdSignals() {
        InterviewQuestionKnowledgePort knowledge = mock(InterviewQuestionKnowledgePort.class);
        when(knowledge.search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IllegalStateException("unavailable"));
        InterviewPreparationExecutor executor = new InterviewPreparationExecutor();
        var coordinator = new ComprehensivePreparationCoordinator(new JdSignalAnalyzer(),
                new ProfileEvidenceAnalyzer(JsonMapper.builder().build()), knowledge, executor);

        var signals = coordinator.collect(snapshot());

        assertThat(signals.jd().capabilitySignals()).isNotEmpty();
        assertThat(signals.knowledgeCandidates()).isEmpty();
    }

    private static InputSnapshot snapshot() {
        UUID interviewId = UUID.randomUUID();
        Instant now = Instant.now();
        return new InputSnapshot(UUID.randomUUID(), interviewId, new ActorId(UUID.randomUUID().toString()),
                "AI_AGENT_DEVELOPMENT", "需要Java、Agent和RAG生产经验", "30-50K",
                null, null, null, "STANDARD", 3600, 1, 0, now, now);
    }
}
