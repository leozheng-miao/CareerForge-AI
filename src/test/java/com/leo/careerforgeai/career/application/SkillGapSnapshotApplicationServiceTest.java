package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapItem;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapStatus;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.application.profile.MemoryProfileQueryApplicationService;
import com.leo.careerforgeai.memory.domain.profile.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.testsupport.MutableCurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证能力差距快照生成、输入版本、幂等复用、引用白名单和owner安全边界
 * @author: Miao Zheng
 * @date: 2026-08-17
 */
@ExtendWith(MockitoExtension.class)
class SkillGapSnapshotApplicationServiceTest {
    private static final ActorId ACTOR_A = new ActorId("actor-gap-a");
    private static final ActorId ACTOR_B = new ActorId("actor-gap-b");
    private static final UUID TARGET_ROLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID JAVA_MEMORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SPRING_MEMORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDE_MEMORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000099");
    private static final Instant NOW = Instant.parse("2026-08-17T04:00:00Z");
    private static final String ALGORITHM_VERSION = DeterministicSkillGapMatcher.ALGORITHM_VERSION;

    @Mock
    private CareerPlanningRepository repository;
    @Mock
    private MemoryProfileQueryApplicationService profileQueryService;
    @Mock
    private DeterministicSkillGapMatcher matcher;

    private MutableCurrentActorProvider currentActorProvider;
    private SkillGapSnapshotApplicationService service;

    @BeforeEach
    void setUp() {
        currentActorProvider = new MutableCurrentActorProvider(ACTOR_A);
        service = new SkillGapSnapshotApplicationService(
                currentActorProvider, repository, profileQueryService,
                matcher, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldGenerateValidatedSnapshotAndPersist() {
        TargetRole targetRole = targetRole(ACTOR_A);
        MemoryItem javaProject = confirmedSkill(
                ACTOR_A, JAVA_MEMORY_ID, "Java", MemorySourceType.PROJECT_EVIDENCE);
        MemoryItem springSelfReport = confirmedSkill(
                ACTOR_A, SPRING_MEMORY_ID, "Spring Boot", MemorySourceType.CONVERSATION_TURN);
        ConfirmedSkillProfile profile = new ConfirmedSkillProfile(
                ACTOR_A, 2, List.of(javaProject, springSelfReport));
        List<GapItem> items = mixedItems(javaProject, springSelfReport);
        stubGenerationInputs(targetRole, profile);
        when(matcher.match(targetRole, profile)).thenReturn(items);

        SkillGapSnapshot result = service.generate(TARGET_ROLE_ID, 2, 2);

        assertThat(result.snapshotId()).isNotNull();
        assertThat(result.ownerId()).isEqualTo(ACTOR_A);
        assertThat(result.targetRoleId()).isEqualTo(TARGET_ROLE_ID);
        assertThat(result.targetRoleVersion()).isEqualTo(2);
        assertThat(result.profileVersion()).isEqualTo(2);
        assertThat(result.algorithmVersion()).isEqualTo(ALGORITHM_VERSION);
        assertThat(result.items()).containsExactlyElementsOf(items);
        assertThat(result.createdAt()).isEqualTo(NOW);

        ArgumentCaptor<SkillGapSnapshot> captor =
                ArgumentCaptor.forClass(SkillGapSnapshot.class);
        verify(repository).insertSkillGapSnapshot(captor.capture());
        assertThat(captor.getValue()).isEqualTo(result);
    }

    @Test
    void shouldRejectStaleTargetVersionBeforeReadingProfile() {
        TargetRole targetRole = targetRole(ACTOR_A);
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.of(targetRole));
        when(repository.findTargetRole(ACTOR_A, TARGET_ROLE_ID)).thenReturn(Optional.of(targetRole));

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 1, 0))
                .isInstanceOf(SkillGapInputVersionConflictException.class)
                .hasMessage("目标岗位版本已经过期，请刷新后重试");

        verifyNoInteractions(profileQueryService, matcher);
        verify(repository, never()).insertSkillGapSnapshot(any());
    }

    @Test
    void shouldReportNoConfirmedTargetRole() {
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 2, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前用户尚未确认目标岗位");

        verifyNoInteractions(profileQueryService, matcher);
    }

    @Test
    void shouldFailClosedWhenLatestTargetBelongsToAnotherOwner() {
        when(repository.findLatestTargetRole(ACTOR_A))
                .thenReturn(Optional.of(targetRole(ACTOR_B)));

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 2, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("最新TargetRole查询结果违反owner边界");

        verifyNoInteractions(profileQueryService, matcher);
    }

    @Test
    void shouldFailClosedForUnknownRequirementReference() {
        TargetRole targetRole = targetRole(ACTOR_A);
        ConfirmedSkillProfile profile = new ConfirmedSkillProfile(ACTOR_A, 0, List.of());
        stubGenerationInputs(targetRole, profile);
        when(matcher.match(targetRole, profile)).thenReturn(List.of(
                new GapItem(UUID.randomUUID(), "unknownRequirements[0]",
                        "未知要求", GapStatus.MISSING, List.of(), "没有证据")
        ));

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 2, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gap引用了目标岗位之外的要求ID");

        verify(repository, never()).insertSkillGapSnapshot(any());
    }

    @Test
    void shouldFailClosedForEvidenceOutsideCurrentProfile() {
        TargetRole targetRole = targetRole(ACTOR_A);
        MemoryItem javaProject = confirmedSkill(
                ACTOR_A, JAVA_MEMORY_ID, "Java", MemorySourceType.PROJECT_EVIDENCE);
        ConfirmedSkillProfile profile =
                new ConfirmedSkillProfile(ACTOR_A, 1, List.of(javaProject));
        stubGenerationInputs(targetRole, profile);
        when(matcher.match(targetRole, profile)).thenReturn(List.of(
                new GapItem(UUID.randomUUID(), "programmingLanguages[0]", "Java",
                        GapStatus.MATCHED, List.of(OUTSIDE_MEMORY_ID), "存在项目证据"),
                missing("backendAndInfrastructureRequirements[0]", "Spring Boot"),
                missing("backendAndInfrastructureRequirements[1]", "MySQL")
        ));

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 2, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gap引用了当前画像之外的Memory证据");

        verify(repository, never()).insertSkillGapSnapshot(any());
    }

    @Test
    void shouldFailClosedForCrossSkillEvidence() {
        TargetRole targetRole = targetRole(ACTOR_A);
        MemoryItem springProject = confirmedSkill(
                ACTOR_A, SPRING_MEMORY_ID, "Spring Boot", MemorySourceType.PROJECT_EVIDENCE);
        ConfirmedSkillProfile profile =
                new ConfirmedSkillProfile(ACTOR_A, 1, List.of(springProject));
        stubGenerationInputs(targetRole, profile);
        when(matcher.match(targetRole, profile)).thenReturn(List.of(
                new GapItem(UUID.randomUUID(), "programmingLanguages[0]", "Java",
                        GapStatus.MATCHED, List.of(SPRING_MEMORY_ID), "存在项目证据"),
                missing("backendAndInfrastructureRequirements[0]", "Spring Boot"),
                missing("backendAndInfrastructureRequirements[1]", "MySQL")
        ));

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 2, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gap引用的Memory与目标要求技能键不一致");

        verify(repository, never()).insertSkillGapSnapshot(any());
    }

    @Test
    void shouldRejectMatchedStatusWithoutProjectEvidence() {
        TargetRole targetRole = targetRole(ACTOR_A);
        MemoryItem javaSelfReport = confirmedSkill(
                ACTOR_A, JAVA_MEMORY_ID, "Java", MemorySourceType.CONVERSATION_TURN);
        ConfirmedSkillProfile profile =
                new ConfirmedSkillProfile(ACTOR_A, 1, List.of(javaSelfReport));
        stubGenerationInputs(targetRole, profile);
        when(matcher.match(targetRole, profile)).thenReturn(List.of(
                new GapItem(UUID.randomUUID(), "programmingLanguages[0]", "Java",
                        GapStatus.MATCHED, List.of(JAVA_MEMORY_ID), "声称存在证据"),
                missing("backendAndInfrastructureRequirements[0]", "Spring Boot"),
                missing("backendAndInfrastructureRequirements[1]", "MySQL")
        ));

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 2, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MATCHED必须引用可信项目证据");

        verify(repository, never()).insertSkillGapSnapshot(any());
    }

    @Test
    void shouldFailClosedWhenRequirementsAreNotFullyCovered() {
        TargetRole targetRole = targetRole(ACTOR_A);
        ConfirmedSkillProfile profile = new ConfirmedSkillProfile(ACTOR_A, 0, List.of());
        stubGenerationInputs(targetRole, profile);
        when(matcher.match(targetRole, profile)).thenReturn(List.of(
                missing("programmingLanguages[0]", "Java")
        ));

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 2, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gap结果没有完整覆盖目标岗位要求ID");

        verify(repository, never()).insertSkillGapSnapshot(any());
    }

    @Test
    void shouldQueryOwnedSnapshot() {
        SkillGapSnapshot snapshot = snapshot(ACTOR_A, 0, missingItems());
        when(repository.findSkillGapSnapshot(ACTOR_A, SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshot));

        assertThat(service.get(SNAPSHOT_ID)).isSameAs(snapshot);
    }

    @Test
    void shouldHideAnotherOwnersSnapshot() {
        currentActorProvider.switchTo(ACTOR_B);
        when(repository.findSkillGapSnapshot(ACTOR_B, SNAPSHOT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(SNAPSHOT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("能力差距快照不存在或不属于当前用户");
    }

    @Test
    void shouldFailClosedWhenRepositoryReturnsForeignSnapshot() {
        SkillGapSnapshot foreign = snapshot(ACTOR_B, 0, missingItems());
        when(repository.findSkillGapSnapshot(ACTOR_A, SNAPSHOT_ID))
                .thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.get(SNAPSHOT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SkillGapSnapshot查询结果违反owner边界");
    }

    @Test
    void shouldReturnExistingSnapshotForSameInputWithoutMatchingOrWriting() {
        TargetRole targetRole = targetRole(ACTOR_A);
        SkillGapSnapshot existing = snapshot(ACTOR_A, 0, missingItems());
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.of(targetRole));
        when(repository.findTargetRole(ACTOR_A, TARGET_ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(matcher.algorithmVersion()).thenReturn(ALGORITHM_VERSION);
        when(repository.findSkillGapSnapshotByInputVersions(
                ACTOR_A, TARGET_ROLE_ID, 2, 0, ALGORITHM_VERSION
        )).thenReturn(Optional.of(existing));

        SkillGapSnapshot result = service.generate(TARGET_ROLE_ID, 2, 0);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(profileQueryService);
        verify(matcher).algorithmVersion();
        verify(matcher, never()).match(any(), any());
        verify(repository, never()).insertSkillGapSnapshot(any());
    }

    @Test
    void shouldRejectStaleProfileVersionAfterSnapshotMissWithoutMatching() {
        TargetRole targetRole = targetRole(ACTOR_A);
        ConfirmedSkillProfile profile = new ConfirmedSkillProfile(ACTOR_A, 2, List.of());
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.of(targetRole));
        when(repository.findTargetRole(ACTOR_A, TARGET_ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(matcher.algorithmVersion()).thenReturn(ALGORITHM_VERSION);
        when(repository.findSkillGapSnapshotByInputVersions(
                ACTOR_A, TARGET_ROLE_ID, 2, 1, ALGORITHM_VERSION
        )).thenReturn(Optional.empty());
        when(profileQueryService.findConfirmedSkillProfile()).thenReturn(profile);

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 2, 1))
                .isInstanceOf(SkillGapInputVersionConflictException.class)
                .hasMessage("技能画像版本已经过期，请刷新后重试");

        verify(repository).findSkillGapSnapshotByInputVersions(
                ACTOR_A, TARGET_ROLE_ID, 2, 1, ALGORITHM_VERSION
        );
        verify(matcher).algorithmVersion();
        verify(matcher, never()).match(any(), any());
        verify(repository, never()).insertSkillGapSnapshot(any());
    }

    @Test
    void shouldFailClosedWhenProfileBelongsToAnotherOwner() {
        TargetRole targetRole = targetRole(ACTOR_A);
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.of(targetRole));
        when(repository.findTargetRole(ACTOR_A, TARGET_ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(matcher.algorithmVersion()).thenReturn(ALGORITHM_VERSION);
        when(repository.findSkillGapSnapshotByInputVersions(
                ACTOR_A, TARGET_ROLE_ID, 2, 0, ALGORITHM_VERSION
        )).thenReturn(Optional.empty());
        when(profileQueryService.findConfirmedSkillProfile())
                .thenReturn(new ConfirmedSkillProfile(ACTOR_B, 0, List.of()));

        assertThatThrownBy(() -> service.generate(TARGET_ROLE_ID, 2, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("技能画像查询结果违反owner边界");

        verify(matcher).algorithmVersion();
        verify(matcher, never()).match(any(), any());
        verify(repository, never()).insertSkillGapSnapshot(any());
    }

    private void stubGenerationInputs(
            TargetRole targetRole,
            ConfirmedSkillProfile profile
    ) {
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.of(targetRole));
        when(repository.findTargetRole(ACTOR_A, TARGET_ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(profileQueryService.findConfirmedSkillProfile()).thenReturn(profile);
        when(matcher.algorithmVersion()).thenReturn(ALGORITHM_VERSION);
        when(repository.findSkillGapSnapshotByInputVersions(
                ACTOR_A, TARGET_ROLE_ID, targetRole.targetRoleVersion(),
                profile.profileVersion(), ALGORITHM_VERSION
        )).thenReturn(Optional.empty());
    }

    private TargetRole targetRole(ActorId ownerId) {
        return TargetRole.createConfirmed(
                TARGET_ROLE_ID, ownerId, 2, "jd-java-002", "a".repeat(64),
                "job-requirements-parser-v1", "job-requirements-prompt-v1",
                requirements(), NOW.minusSeconds(120)
        );
    }

    private JobRequirements requirements() {
        return new JobRequirements(
                "Java开发工程师",
                List.of("Java"),
                List.of("Spring Boot", "MySQL"),
                List.of(), List.of(), List.of(), List.of(),
                List.of("开发后端服务"),
                List.of("Java", "Spring Boot")
        );
    }

    private List<GapItem> mixedItems(
            MemoryItem javaProject,
            MemoryItem springSelfReport
    ) {
        return List.of(
                new GapItem(UUID.randomUUID(), "programmingLanguages[0]", "Java",
                        GapStatus.MATCHED, List.of(javaProject.memoryId()), "存在同技能的已确认项目证据"),
                new GapItem(UUID.randomUUID(), "backendAndInfrastructureRequirements[0]", "Spring Boot",
                        GapStatus.UNVERIFIED, List.of(springSelfReport.memoryId()),
                        "存在同技能的已确认自述，但缺少项目证据"),
                missing("backendAndInfrastructureRequirements[1]", "MySQL")
        );
    }

    private List<GapItem> missingItems() {
        return List.of(
                missing("programmingLanguages[0]", "Java"),
                missing("backendAndInfrastructureRequirements[0]", "Spring Boot"),
                missing("backendAndInfrastructureRequirements[1]", "MySQL")
        );
    }

    private GapItem missing(String reference, String text) {
        return new GapItem(
                UUID.randomUUID(), reference, text, GapStatus.MISSING,
                List.of(), "当前已确认技能画像中没有同技能证据"
        );
    }

    private SkillGapSnapshot snapshot(
            ActorId ownerId,
            long profileVersion,
            List<GapItem> items
    ) {
        return SkillGapSnapshot.create(
                SNAPSHOT_ID, ownerId, TARGET_ROLE_ID, 2,
                profileVersion, ALGORITHM_VERSION,
                items, NOW.minusSeconds(10)
        );
    }

    private MemoryItem confirmedSkill(
            ActorId ownerId,
            UUID memoryId,
            String skill,
            MemorySourceType sourceType
    ) {
        String sourceId = sourceType.name().toLowerCase() + "-" + memoryId;
        MemoryItem pending = MemoryItem.createPending(
                memoryId, ownerId, MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence(skill),
                "已使用" + skill + "完成项目开发",
                new MemorySource(sourceType, sourceId, "b".repeat(64)),
                List.of(sourceId), NOW.minusSeconds(90)
        );
        MemoryDecision decision = MemoryDecision.create(
                UUID.randomUUID(), pending, ownerId,
                MemoryDecisionType.CONFIRM, null,
                "确认技能证据", NOW.minusSeconds(60)
        );
        return pending.applyDecision(decision);
    }
}