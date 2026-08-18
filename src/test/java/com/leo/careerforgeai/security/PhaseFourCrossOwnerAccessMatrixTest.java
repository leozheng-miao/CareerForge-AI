package com.leo.careerforgeai.security;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.skillgap.DeterministicSkillGapMatcher;
import com.leo.careerforgeai.career.application.skillgap.SkillGapSnapshotApplicationService;
import com.leo.careerforgeai.career.application.training.TrainingPlanApplicationService;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.application.profile.MemoryProfileQueryApplicationService;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.testsupport.MutableCurrentActorProvider;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 通过应用服务量化Session、Turn、Memory、Gap和Plan的跨owner读取阻断结果
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
@ExtendWith(MockitoExtension.class)
class PhaseFourCrossOwnerAccessMatrixTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final ActorId ACTOR_B = new ActorId("actor-b");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-18T06:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private CoachingConversationRepository conversationRepository;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private CareerPlanningRepository careerPlanningRepository;

    @Mock
    private CoachingSession actorASession;

    @Mock
    private ConversationTurn actorATurn;

    @Mock
    private MemoryItem actorAMemory;

    @Mock
    private SkillGapSnapshot actorASnapshot;

    @Mock
    private TrainingPlan actorAPlan;

    private MutableCurrentActorProvider actorProvider;
    private CoachingSessionApplicationService sessionService;
    private MemoryProfileQueryApplicationService memoryProfileService;
    private SkillGapSnapshotApplicationService gapService;
    private TrainingPlanApplicationService planService;

    @BeforeEach
    void setUp() {
        actorProvider = new MutableCurrentActorProvider(ACTOR_A);
        sessionService = new CoachingSessionApplicationService(
                actorProvider,
                conversationRepository,
                CLOCK
        );
        memoryProfileService = new MemoryProfileQueryApplicationService(
                actorProvider,
                memoryRepository
        );
        gapService = new SkillGapSnapshotApplicationService(
                actorProvider,
                careerPlanningRepository,
                memoryProfileService,
                new DeterministicSkillGapMatcher(),
                CLOCK
        );
        planService = new TrainingPlanApplicationService(
                actorProvider,
                careerPlanningRepository,
                CLOCK
        );

        when(conversationRepository.findSession(ACTOR_A, SESSION_ID))
                .thenReturn(Optional.of(actorASession));
        when(conversationRepository.findRecentTurns(
                ACTOR_A,
                SESSION_ID,
                CoachingSessionApplicationService.DEFAULT_RECENT_TURN_LIMIT
        )).thenReturn(List.of(actorATurn));
        when(memoryRepository.findConfirmedByOwner(ACTOR_A))
                .thenReturn(List.of(actorAMemory));
        when(actorAMemory.ownerId()).thenReturn(ACTOR_A);
        when(actorAMemory.status()).thenReturn(MemoryStatus.CONFIRMED);
        when(careerPlanningRepository.findSkillGapSnapshot(ACTOR_A, SNAPSHOT_ID))
                .thenReturn(Optional.of(actorASnapshot));
        when(actorASnapshot.ownerId()).thenReturn(ACTOR_A);
        when(careerPlanningRepository.findTrainingPlan(ACTOR_A, PLAN_ID))
                .thenReturn(Optional.of(actorAPlan));
        when(actorAPlan.ownerId()).thenReturn(ACTOR_A);

        when(conversationRepository.findSession(ACTOR_B, SESSION_ID))
                .thenReturn(Optional.empty());
        when(memoryRepository.findConfirmedByOwner(ACTOR_B))
                .thenReturn(List.of());
        when(careerPlanningRepository.findSkillGapSnapshot(ACTOR_B, SNAPSHOT_ID))
                .thenReturn(Optional.empty());
        when(careerPlanningRepository.findTrainingPlan(ACTOR_B, PLAN_ID))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldBlockAllCrossOwnerReadAttemptsThroughApplicationServices() {
        assertThat(sessionService.getSession(SESSION_ID)).isSameAs(actorASession);
        assertThat(sessionService.getRecentTurns(SESSION_ID)).containsExactly(actorATurn);
        assertThat(memoryProfileService.findConfirmedProfile()).containsExactly(actorAMemory);
        assertThat(gapService.get(SNAPSHOT_ID)).isSameAs(actorASnapshot);
        assertThat(planService.get(PLAN_ID)).isSameAs(actorAPlan);

        actorProvider.switchTo(ACTOR_B);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();

        assertBlocked(attempts, blocked, () -> sessionService.getSession(SESSION_ID));
        assertBlocked(attempts, blocked, () -> sessionService.getRecentTurns(SESSION_ID));

        attempts.incrementAndGet();
        assertThat(memoryProfileService.findConfirmedProfile()).isEmpty();
        blocked.incrementAndGet();

        assertBlocked(attempts, blocked, () -> gapService.get(SNAPSHOT_ID));
        assertBlocked(attempts, blocked, () -> planService.get(PLAN_ID));

        assertThat(attempts).hasValue(5);
        assertThat(blocked).hasValue(5);

        verify(conversationRepository, times(2)).findSession(ACTOR_B, SESSION_ID);
        verify(conversationRepository, never()).findRecentTurns(
                ACTOR_B,
                SESSION_ID,
                CoachingSessionApplicationService.DEFAULT_RECENT_TURN_LIMIT
        );
        verify(memoryRepository).findConfirmedByOwner(ACTOR_B);
        verify(careerPlanningRepository).findSkillGapSnapshot(ACTOR_B, SNAPSHOT_ID);
        verify(careerPlanningRepository).findTrainingPlan(ACTOR_B, PLAN_ID);
    }

    private static void assertBlocked(
            AtomicInteger attempts,
            AtomicInteger blocked,
            ThrowingCallable action
    ) {
        attempts.incrementAndGet();
        assertThatThrownBy(action)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在或不属于当前用户");
        blocked.incrementAndGet();
    }
}