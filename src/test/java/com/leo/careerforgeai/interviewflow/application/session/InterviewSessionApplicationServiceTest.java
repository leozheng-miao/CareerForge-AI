package com.leo.careerforgeai.interviewflow.application.session;

import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证快速面试幂等创建、owner隔离和正式进入规则
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@ExtendWith(MockitoExtension.class)
class InterviewSessionApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("quick-owner-a");
    private static final UUID REQUEST_ID =
            UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("31000000-0000-0000-0000-000000000002");
    private static final Instant NOW =
            Instant.parse("2026-10-06T02:00:00Z");

    @Mock
    private InterviewSessionRepository sessionRepository;

    @Mock
    private InterviewInputSnapshotRepository inputSnapshotRepository;

    @Mock
    private InterviewTaskRepository taskRepository;

    private InterviewSessionApplicationService service;

    @BeforeEach
    void setUp() {
        CurrentActorProvider currentActorProvider = () -> OWNER;
        service = new InterviewSessionApplicationService(
                currentActorProvider,
                sessionRepository,
                inputSnapshotRepository,
                taskRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreatePreparingQuickInterviewIdempotentlyByOwnerAndRequest() {
        AtomicReference<InterviewAggregate> stored = new AtomicReference<>();
        when(sessionRepository.findByRequestId(OWNER, REQUEST_ID))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(sessionRepository.findInProgress(OWNER)).thenReturn(Optional.empty());
        when(sessionRepository.claim(any())).thenAnswer(invocation -> {
            InterviewAggregate candidate = invocation
                    .getArgument(0, InterviewSessionRepository.SessionClaim.class)
                    .candidate();
            stored.set(candidate);
            return candidate;
        });
        when(taskRepository.enqueue(any())).thenReturn(true);

        CreateQuickInterviewCommand command =
                new CreateQuickInterviewCommand(
                        REQUEST_ID,
                        CreateQuickInterviewCommand.AI_AGENT_DEVELOPMENT
                );

        var first = service.createQuick(command);
        var replay = service.createQuick(command);

        assertThat(first).isEqualTo(replay);
        assertThat(first.mode().name()).isEqualTo("QUICK");
        assertThat(first.stage()).isEqualTo(SessionStage.PREPARING);
        assertThat(first.ownerId()).isEqualTo(OWNER);

        ArgumentCaptor<InterviewInputSnapshotRepository.InputSnapshot>
                inputCaptor = ArgumentCaptor.forClass(
                        InterviewInputSnapshotRepository.InputSnapshot.class
                );
        verify(inputSnapshotRepository).insert(inputCaptor.capture());
        assertThat(inputCaptor.getValue().direction())
                .isEqualTo(CreateQuickInterviewCommand.AI_AGENT_DEVELOPMENT);
        assertThat(inputCaptor.getValue().durationSeconds()).isEqualTo(3600);
        verify(sessionRepository, times(1)).claim(any());
        verify(taskRepository, times(1)).enqueue(any());
    }

    @Test
    void shouldRejectAnyDirectionOtherThanTheFixedQuickDirection() {
        assertThatThrownBy(() -> new CreateQuickInterviewCommand(
                REQUEST_ID,
                "JAVA_BACKEND"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI_AGENT_DEVELOPMENT");
    }

    @Test
    void shouldHideAnotherOwnersInterviewAsNotFound() {
        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(INTERVIEW_ID))
                .isInstanceOf(InterviewFlowNotFoundException.class);
    }

    @Test
    void shouldEnterOnlyFromReadyAndTreatRepeatedEnterAsIdempotent() {
        InterviewAggregate ready = readyInterview(INTERVIEW_ID);
        InterviewAggregate interviewing = new InterviewStateMachine(
                new QuickInterviewPolicy()
        ).transition(ready, InterviewEvent.FORMAL_START_REQUESTED).aggregate();

        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(ready), Optional.of(interviewing));
        when(sessionRepository.findInProgress(OWNER)).thenReturn(Optional.empty());
        when(sessionRepository.updateIfVersionMatches(
                OWNER,
                interviewing,
                ready.session().version()
        )).thenReturn(true);

        var first = service.enter(
                INTERVIEW_ID,
                REQUEST_ID,
                ready.session().version()
        );
        var replay = service.enter(
                INTERVIEW_ID,
                REQUEST_ID,
                ready.session().version()
        );

        assertThat(first).isEqualTo(interviewing.session());
        assertThat(replay).isEqualTo(first);
        verify(sessionRepository, times(1)).updateIfVersionMatches(
                OWNER,
                interviewing,
                ready.session().version()
        );
    }

    @Test
    void shouldRejectFormalEntryWhilePreparationIsIncomplete() {
        InterviewAggregate preparing = InterviewAggregate.createQuick(
                INTERVIEW_ID,
                OWNER,
                NOW,
                new QuickInterviewPolicy()
        );
        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(preparing));

        assertThatThrownBy(() -> service.enter(
                INTERVIEW_ID,
                REQUEST_ID,
                preparing.session().version()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY");

        verify(sessionRepository, never()).updateIfVersionMatches(
                any(),
                any(),
                anyLong()
        );
    }

    private InterviewAggregate readyInterview(UUID interviewId) {
        InterviewAggregate preparing = InterviewAggregate.createQuick(
                interviewId,
                OWNER,
                NOW,
                new QuickInterviewPolicy()
        );
        return new InterviewStateMachine(new QuickInterviewPolicy())
                .transition(preparing, InterviewEvent.PREPARATION_COMPLETED)
                .aggregate();
    }
}
