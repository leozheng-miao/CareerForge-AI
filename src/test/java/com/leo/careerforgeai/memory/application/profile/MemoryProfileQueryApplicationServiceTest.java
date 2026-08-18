package com.leo.careerforgeai.memory.application.profile;

import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.profile.LearningPreferenceKey;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * @program: CareerForge-AI
 * @description: 验证PENDING候选和CONFIRMED画像查询的用户隔离、状态边界和只读结果
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@ExtendWith(MockitoExtension.class)
class MemoryProfileQueryApplicationServiceTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final ActorId ACTOR_B = new ActorId("actor-b");
    private static final Instant NOW = Instant.parse("2026-08-14T04:00:00Z");

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private MemoryRepository memoryRepository;

    private MemoryProfileQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new MemoryProfileQueryApplicationService(
                currentActorProvider,
                memoryRepository
        );
        when(currentActorProvider.currentActor()).thenReturn(ACTOR_A);
    }

    @Test
    void shouldReturnOnlyCurrentActorPendingCandidates() {
        MemoryItem first = pendingMemory(UUID.randomUUID(), ACTOR_A, "我每周可以学习10小时");
        MemoryItem second = pendingMemory(UUID.randomUUID(), ACTOR_A, "我每周可以学习6小时");
        when(memoryRepository.findPendingByOwner(ACTOR_A)).thenReturn(List.of(first, second));

        assertThat(service.findPendingCandidates()).containsExactly(first, second);
        verify(memoryRepository).findPendingByOwner(ACTOR_A);
    }

    @Test
    void shouldFailClosedWhenRepositoryReturnsAnotherOwnersMemory() {
        MemoryItem leaked = pendingMemory(UUID.randomUUID(), ACTOR_B, "其他用户的时间限制");
        when(memoryRepository.findPendingByOwner(ACTOR_A)).thenReturn(List.of(leaked));

        assertThatThrownBy(service::findPendingCandidates)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PENDING Memory查询结果违反owner或状态边界");
    }

    @Test
    void shouldFailClosedWhenRepositoryReturnsNonPendingMemory() {
        MemoryItem candidate = pendingMemory(UUID.randomUUID(), ACTOR_A, "我每周可以学习10小时");
        MemoryItem confirmed = candidate.applyDecision(
                MemoryDecision.create(
                        UUID.randomUUID(),
                        candidate,
                        ACTOR_A,
                        MemoryDecisionType.CONFIRM,
                        null,
                        "用户确认",
                        NOW.plusSeconds(1)
                )
        );
        when(memoryRepository.findPendingByOwner(ACTOR_A)).thenReturn(List.of(confirmed));

        assertThatThrownBy(service::findPendingCandidates)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PENDING Memory查询结果违反owner或状态边界");
    }

    @Test
    void shouldReturnOnlyCurrentActorConfirmedProfile() {
        MemoryItem confirmed = confirmedMemory(
                UUID.randomUUID(),
                ACTOR_A,
                "我每周可以学习10小时"
        );
        when(memoryRepository.findConfirmedByOwner(ACTOR_A))
                .thenReturn(List.of(confirmed));

        assertThat(service.findConfirmedProfile()).containsExactly(confirmed);
        verify(memoryRepository).findConfirmedByOwner(ACTOR_A);
    }

    @Test
    void shouldFailClosedWhenConfirmedQueryReturnsNonConfirmedMemory() {
        MemoryItem pending = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                "尚未确认的时间限制"
        );
        when(memoryRepository.findConfirmedByOwner(ACTOR_A))
                .thenReturn(List.of(pending));

        assertThatThrownBy(service::findConfirmedProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CONFIRMED Memory查询结果违反owner或状态边界");
    }

    @Test
    void shouldBuildVersionedConfirmedSkillProfile() {
        MemoryItem skill = confirmedSkillMemory(UUID.randomUUID(), ACTOR_A, "Spring Boot");
        MemoryItem timeConstraint = confirmedMemory(UUID.randomUUID(), ACTOR_A, "我每周可以学习10小时");
        when(memoryRepository.countSkillProfileChanges(ACTOR_A)).thenReturn(3L);
        when(memoryRepository.findConfirmedByOwner(ACTOR_A)).thenReturn(List.of(timeConstraint, skill));

        ConfirmedSkillProfile result = service.findConfirmedSkillProfile();

        assertThat(result.ownerId()).isEqualTo(ACTOR_A);
        assertThat(result.profileVersion()).isEqualTo(3);
        assertThat(result.skillEvidence()).containsExactly(skill);
        verify(memoryRepository, times(2)).countSkillProfileChanges(ACTOR_A);
    }

    @Test
    void shouldAllowEmptySkillProfileAfterEarlierRevocations() {
        when(memoryRepository.countSkillProfileChanges(ACTOR_A)).thenReturn(4L);
        when(memoryRepository.findConfirmedByOwner(ACTOR_A)).thenReturn(List.of());

        ConfirmedSkillProfile result = service.findConfirmedSkillProfile();

        assertThat(result.profileVersion()).isEqualTo(4);
        assertThat(result.skillEvidence()).isEmpty();
    }

    @Test
    void shouldFailClosedWhenSkillProfileChangesDuringRead() {
        when(memoryRepository.countSkillProfileChanges(ACTOR_A)).thenReturn(2L, 3L);
        when(memoryRepository.findConfirmedByOwner(ACTOR_A)).thenReturn(List.of());

        assertThatThrownBy(service::findConfirmedSkillProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("技能画像读取期间发生变化，请重试");
    }

    @Test
    void shouldFailClosedWhenConfirmedEvidenceHasNoDecisionHistory() {
        MemoryItem skill = confirmedSkillMemory(UUID.randomUUID(), ACTOR_A, "Spring Boot");
        when(memoryRepository.countSkillProfileChanges(ACTOR_A)).thenReturn(0L);
        when(memoryRepository.findConfirmedByOwner(ACTOR_A)).thenReturn(List.of(skill));

        assertThatThrownBy(service::findConfirmedSkillProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("profileVersion小于当前技能证据数量");
    }

    @Test
    void shouldReturnConfirmedPlanningMemoriesInStableOrder() {
        MemoryItem learningPreference = confirmedPlanningMemory(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                MemoryType.LEARNING_PREFERENCE,
                MemoryNormalizedKey.learningPreference(
                        LearningPreferenceKey.CONTENT_FORMAT
                ),
                "我更喜欢项目驱动的学习材料"
        );
        MemoryItem timeConstraint = confirmedPlanningMemory(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(
                        TimeConstraintKey.WEEKLY_HOURS
                ),
                "我每周可以学习10小时"
        );
        MemoryItem skillEvidence = confirmedSkillMemory(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                ACTOR_A,
                "Spring Boot"
        );
        when(memoryRepository.findConfirmedByOwner(ACTOR_A))
                .thenReturn(List.of(timeConstraint, skillEvidence, learningPreference));

        List<MemoryItem> result =
                service.findConfirmedPlanningMemories();

        assertThat(result).containsExactly(
                learningPreference,
                timeConstraint
        );
        verify(memoryRepository).findConfirmedByOwner(ACTOR_A);
    }

    private MemoryItem confirmedSkillMemory(UUID memoryId, ActorId ownerId, String skillName) {
        MemoryItem candidate = MemoryItem.createPending(
                memoryId,
                ownerId,
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence(skillName),
                "项目中使用" + skillName + "开发并完成自动化测试",
                new MemorySource(MemorySourceType.PROJECT_EVIDENCE, "project-" + memoryId, "b".repeat(64)),
                List.of("project-" + memoryId),
                NOW
        );
        return candidate.applyDecision(MemoryDecision.create(
                UUID.randomUUID(),
                candidate,
                ownerId,
                MemoryDecisionType.CONFIRM,
                null,
                "用户确认项目证据",
                NOW.plusSeconds(1)
        ));
    }

    private MemoryItem confirmedMemory(
            UUID memoryId,
            ActorId ownerId,
            String content
    ) {
        MemoryItem candidate = pendingMemory(memoryId, ownerId, content);
        MemoryDecision decision = MemoryDecision.create(
                UUID.randomUUID(),
                candidate,
                ownerId,
                MemoryDecisionType.CONFIRM,
                null,
                "用户确认",
                NOW.plusSeconds(1)
        );
        return candidate.applyDecision(decision);
    }

    private MemoryItem confirmedPlanningMemory(
            UUID memoryId,
            MemoryType type,
            MemoryNormalizedKey normalizedKey,
            String content
    ) {
        MemoryItem candidate = MemoryItem.createPending(
                memoryId,
                ACTOR_A,
                type,
                normalizedKey,
                content,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        "turn-" + memoryId,
                        "c".repeat(64)
                ),
                List.of("turn-" + memoryId),
                NOW
        );
        return candidate.applyDecision(
                MemoryDecision.create(
                        UUID.randomUUID(),
                        candidate,
                        ACTOR_A,
                        MemoryDecisionType.CONFIRM,
                        null,
                        "用户确认计划约束",
                        NOW.plusSeconds(1)
                )
        );
    }

    private MemoryItem pendingMemory(UUID memoryId, ActorId ownerId, String content) {
        return MemoryItem.createExtractedPending(
                memoryId,
                ownerId,
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                content,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        "turn-" + memoryId,
                        "a".repeat(64)
                ),
                "model-request-" + memoryId,
                new BigDecimal("0.90"),
                null,
                List.of("turn-" + memoryId),
                NOW
        );
    }
}