package com.leo.careerforgeai.career.application;


import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TargetRoleDraft;
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
 * @description: 目标岗位草案确认、不可变版本和owner安全边界应用服务测试
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@ExtendWith(MockitoExtension.class)
class TargetRoleApplicationServiceTest {
    private static final ActorId ACTOR_A = new ActorId("actor-target-role-a");
    private static final ActorId ACTOR_B = new ActorId("actor-target-role-b");
    private static final UUID DRAFT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ROLE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-08-16T14:00:00Z");
    private static final String SOURCE_REF = "jd-java-agent-001";
    private static final String SOURCE_HASH = "a".repeat(64);
    private static final String PARSER_VERSION = "job-requirements-parser-v1";
    private static final String PROMPT_VERSION = "job-requirements-prompt-v1";

    @Mock
    private CareerPlanningRepository repository;

    private MutableCurrentActorProvider currentActorProvider;
    private TargetRoleApplicationService service;

    @BeforeEach
    void setUp() {
        currentActorProvider = new MutableCurrentActorProvider(ACTOR_A);
        service = new TargetRoleApplicationService(currentActorProvider, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldConfirmFirstDraftAsImmutableVersionOne() {
        TargetRoleDraft draft = pendingDraft(ACTOR_A);
        when(repository.findTargetRoleDraft(ACTOR_A, DRAFT_ID)).thenReturn(Optional.of(draft));
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.empty());

        TargetRole result = service.confirmDraft(DRAFT_ID, 0);

        assertThat(result.targetRoleId()).isNotNull();
        assertThat(result.ownerId()).isEqualTo(ACTOR_A);
        assertThat(result.targetRoleVersion()).isEqualTo(1);
        assertThat(result.sourceRef()).isEqualTo(SOURCE_REF);
        assertThat(result.sourceHash()).isEqualTo(SOURCE_HASH);
        assertThat(result.parserVersion()).isEqualTo(PARSER_VERSION);
        assertThat(result.promptVersion()).isEqualTo(PROMPT_VERSION);
        assertThat(result.requirementsSnapshot()).isEqualTo(requirements());
        assertThat(result.confirmedAt()).isEqualTo(NOW);

        ArgumentCaptor<TargetRoleDraft> draftCaptor = ArgumentCaptor.forClass(TargetRoleDraft.class);
        ArgumentCaptor<TargetRole> targetRoleCaptor = ArgumentCaptor.forClass(TargetRole.class);
        verify(repository).confirmTargetRoleDraft(eq(ACTOR_A), draftCaptor.capture(), targetRoleCaptor.capture(), eq(0L));

        TargetRoleDraft confirmedDraft = draftCaptor.getValue();
        assertThat(confirmedDraft.status()).isEqualTo(TargetRoleDraft.Status.CONFIRMED);
        assertThat(confirmedDraft.version()).isEqualTo(1);
        assertThat(confirmedDraft.confirmedTargetRoleId()).isEqualTo(result.targetRoleId());
        assertThat(confirmedDraft.confirmedTargetRoleVersion()).isEqualTo(1);
        assertThat(confirmedDraft.confirmedAt()).isEqualTo(NOW);
        assertThat(targetRoleCaptor.getValue()).isEqualTo(result);
    }

    @Test
    void shouldCreateNextVersionFromLatestTargetRole() {
        TargetRoleDraft draft = pendingDraft(ACTOR_A);
        TargetRole previous = targetRole(ACTOR_A, TARGET_ROLE_ID, 1, NOW.minusSeconds(30));
        when(repository.findTargetRoleDraft(ACTOR_A, DRAFT_ID)).thenReturn(Optional.of(draft));
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.of(previous));

        TargetRole result = service.confirmDraft(DRAFT_ID, 0);

        assertThat(result.targetRoleVersion()).isEqualTo(2);
        assertThat(result.targetRoleId()).isNotEqualTo(previous.targetRoleId());
        assertThat(result.confirmedAt()).isEqualTo(NOW);
        verify(repository).confirmTargetRoleDraft(eq(ACTOR_A), any(TargetRoleDraft.class), eq(result), eq(0L));
    }

    @Test
    void shouldRejectStaleDraftVersionWithoutWriting() {
        when(repository.findTargetRoleDraft(ACTOR_A, DRAFT_ID)).thenReturn(Optional.of(pendingDraft(ACTOR_A)));

        assertThatThrownBy(() -> service.confirmDraft(DRAFT_ID, 1))
                .isInstanceOf(TargetRoleVersionConflictException.class)
                .hasMessage("目标岗位草案版本已经过期，请刷新后重试");

        verify(repository, never()).findLatestTargetRole(any());
        verify(repository, never()).confirmTargetRoleDraft(any(), any(), any(), anyLong());
    }

    @Test
    void shouldReturnExistingTargetRoleForRepeatedConfirmation() {
        TargetRole existing = targetRole(ACTOR_A, TARGET_ROLE_ID, 1, NOW);
        TargetRoleDraft confirmedDraft = pendingDraft(ACTOR_A).confirm(existing, NOW);
        when(repository.findTargetRoleDraft(ACTOR_A, DRAFT_ID)).thenReturn(Optional.of(confirmedDraft));
        when(repository.findTargetRole(ACTOR_A, TARGET_ROLE_ID)).thenReturn(Optional.of(existing));

        TargetRole result = service.confirmDraft(DRAFT_ID, 0);

        assertThat(result).isSameAs(existing);
        verify(repository, never()).findLatestTargetRole(any());
        verify(repository, never()).confirmTargetRoleDraft(any(), any(), any(), anyLong());
    }

    @Test
    void shouldHideDraftFromAnotherActor() {
        currentActorProvider.switchTo(ACTOR_B);
        when(repository.findTargetRoleDraft(ACTOR_B, DRAFT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmDraft(DRAFT_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("目标岗位草案不存在或不属于当前用户");

        verify(repository, never()).confirmTargetRoleDraft(any(), any(), any(), anyLong());
    }

    @Test
    void shouldHideTargetRoleFromAnotherActor() {
        currentActorProvider.switchTo(ACTOR_B);
        when(repository.findTargetRole(ACTOR_B, TARGET_ROLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TARGET_ROLE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("目标岗位不存在或不属于当前用户");
    }

    @Test
    void shouldFailClosedWhenRepositoryReturnsAnotherOwnersDraft() {
        when(repository.findTargetRoleDraft(ACTOR_A, DRAFT_ID)).thenReturn(Optional.of(pendingDraft(ACTOR_B)));

        assertThatThrownBy(() -> service.confirmDraft(DRAFT_ID, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("目标岗位草案查询结果违反owner边界");

        verify(repository, never()).findLatestTargetRole(any());
        verify(repository, never()).confirmTargetRoleDraft(any(), any(), any(), anyLong());
    }

    @Test
    void shouldFailClosedWhenLatestQueryReturnsAnotherOwnersTargetRole() {
        TargetRole foreignTargetRole = targetRole(ACTOR_B, TARGET_ROLE_ID, 1, NOW.minusSeconds(30));
        when(repository.findTargetRoleDraft(ACTOR_A, DRAFT_ID)).thenReturn(Optional.of(pendingDraft(ACTOR_A)));
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.of(foreignTargetRole));

        assertThatThrownBy(() -> service.confirmDraft(DRAFT_ID, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TargetRole查询结果违反owner边界");

        verify(repository, never()).confirmTargetRoleDraft(any(), any(), any(), anyLong());
    }

    @Test
    void shouldReportNoConfirmedTargetRole() {
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.empty());

        assertThatThrownBy(service::getLatest)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前用户尚未确认目标岗位");
    }

    @Test
    void shouldQueryOwnedTargetRoleByIdAndLatest() {
        TargetRole targetRole = targetRole(ACTOR_A, TARGET_ROLE_ID, 1, NOW);
        when(repository.findTargetRole(ACTOR_A, TARGET_ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(repository.findLatestTargetRole(ACTOR_A)).thenReturn(Optional.of(targetRole));

        assertThat(service.get(TARGET_ROLE_ID)).isSameAs(targetRole);
        assertThat(service.getLatest()).isSameAs(targetRole);
    }

    private TargetRoleDraft pendingDraft(ActorId ownerId) {
        return TargetRoleDraft.createPending(DRAFT_ID, ownerId, SOURCE_REF, SOURCE_HASH, PARSER_VERSION,
                PROMPT_VERSION, requirements(), NOW.minusSeconds(60));
    }

    private TargetRole targetRole(ActorId ownerId, UUID targetRoleId, long version, Instant confirmedAt) {
        return TargetRole.createConfirmed(targetRoleId, ownerId, version, SOURCE_REF, SOURCE_HASH,
                PARSER_VERSION, PROMPT_VERSION, requirements(), confirmedAt);
    }

    private JobRequirements requirements() {
        return new JobRequirements("Java开发工程师", List.of("Java"), List.of("Spring Boot", "MySQL"),
                List.of("Agent开发"), List.of("RAG"), List.of("JUnit 5"), List.of(),
                List.of("开发后端服务"), List.of("Java", "Spring Boot"));
    }
}