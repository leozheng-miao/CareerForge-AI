package com.leo.careerforgeai.memory.infrastructure.memory;

import com.leo.careerforgeai.interview.domain.InterviewReport;
import com.leo.careerforgeai.interview.domain.InterviewReportConfirmation;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证已确认报告Memory建议生成PENDING候选、稳定重放及旧版payload拒绝
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
class MemoryInterviewSuggestionAdapterTest {

    private static final ActorId OWNER = new ActorId("memory-adapter-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final UUID SUGGESTION_ID = UUID.fromString("60000000-0000-0000-0000-000000000003");
    private static final UUID CONFIRMATION_ID = UUID.fromString("60000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-08-30T01:00:00Z");

    private MemoryRepository repository;
    private AtomicReference<MemoryItem> storedMemory;
    private MemoryInterviewSuggestionAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(MemoryRepository.class);
        storedMemory = new AtomicReference<>();

        when(repository.findById(eq(OWNER), any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(storedMemory.get()));
        doAnswer(invocation -> {
            storedMemory.set(invocation.getArgument(0));
            return null;
        }).when(repository).insert(any(MemoryItem.class));

        adapter = new MemoryInterviewSuggestionAdapter(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreatePendingMemoryAndReplayStableId() {
        InterviewReport report = structuredReport();
        InterviewReportConfirmation.Decision decision = confirmedDecision();

        UUID first = adapter.apply(report, decision);
        UUID replay = adapter.apply(report, decision);

        assertThat(replay).isEqualTo(first);
        assertThat(storedMemory.get().memoryId()).isEqualTo(first);
        assertThat(storedMemory.get().ownerId()).isEqualTo(OWNER);
        assertThat(storedMemory.get().type()).isEqualTo(MemoryType.SKILL_EVIDENCE);
        assertThat(storedMemory.get().status()).isEqualTo(MemoryStatus.PENDING);
        assertThat(storedMemory.get().normalizedKey().value()).isEqualTo("spring boot");
        assertThat(storedMemory.get().source().sourceType())
                .isEqualTo(MemorySourceType.INTERVIEW_REPORT);
        assertThat(storedMemory.get().source().sourceId())
                .isEqualTo(SUGGESTION_ID.toString());
        verify(repository, times(1)).insert(any(MemoryItem.class));
    }

    @Test
    void shouldRejectLegacyPayloadWithoutWritingMemory() {
        InterviewReport report = legacyReport();

        assertThatThrownBy(() -> adapter.apply(report, confirmedDecision()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("旧版字符串Memory建议不可执行，请重新生成报告");

        verify(repository, never()).insert(any(MemoryItem.class));
    }

    private InterviewReport structuredReport() {
        InterviewReport.MemoryCandidatePayload payload =
                new InterviewReport.MemoryCandidatePayload(
                        "SpringBoot",
                        "能够使用Spring Boot实现可靠的Agent应用服务。"
                );

        return report(new InterviewReport.Suggestion(
                SUGGESTION_ID,
                REPORT_ID,
                INTERVIEW_ID,
                OWNER,
                InterviewReport.SuggestionType.MEMORY_CANDIDATE,
                1,
                payload.content(),
                payload,
                "a".repeat(64),
                NOW.minusSeconds(60)
        ));
    }

    private InterviewReport legacyReport() {
        InterviewReport.LegacyPayload payload =
                new InterviewReport.LegacyPayload("旧版不可执行Memory建议");

        return report(new InterviewReport.Suggestion(
                SUGGESTION_ID,
                REPORT_ID,
                INTERVIEW_ID,
                OWNER,
                InterviewReport.SuggestionType.MEMORY_CANDIDATE,
                1,
                payload.content(),
                payload,
                "b".repeat(64),
                NOW.minusSeconds(60)
        ));
    }

    private InterviewReport report(InterviewReport.Suggestion suggestion) {
        return InterviewReport.pendingConfirmation(
                REPORT_ID,
                INTERVIEW_ID,
                OWNER,
                List.of("能够说明Spring Boot基础机制。"),
                List.of(),
                List.of(),
                List.of("补充可靠性实验。"),
                List.of(suggestion),
                "report-request-memory",
                "report-coach-v2",
                "c".repeat(64),
                "d".repeat(64),
                NOW.minusSeconds(60)
        );
    }

    private InterviewReportConfirmation.Decision confirmedDecision() {
        return InterviewReportConfirmation.Decision.confirmed(
                UUID.fromString("60000000-0000-0000-0000-000000000005"),
                CONFIRMATION_ID,
                SUGGESTION_ID,
                REPORT_ID,
                INTERVIEW_ID,
                OWNER,
                NOW.minusSeconds(30)
        );
    }
}