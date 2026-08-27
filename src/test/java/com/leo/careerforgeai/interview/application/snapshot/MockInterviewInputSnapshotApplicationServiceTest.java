package com.leo.careerforgeai.interview.application.snapshot;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.interview.application.evidence.PersonalEvidenceArtifactFactory;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.domain.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceType;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证仅简历快照、确定性Hash及过期或撤销输入的fail-closed边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
class MockInterviewInputSnapshotApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private static final ActorId OWNER = new ActorId("owner-a");

    @Test
    void shouldCreateSnapshotUsingOnlyConfirmedTargetRoleAndResume() {
        CareerPlanningRepository careerRepository = mock(CareerPlanningRepository.class);
        PersonalEvidenceArtifactRepository evidenceRepository =
                mock(PersonalEvidenceArtifactRepository.class);
        MockInterviewInputSnapshotRepository snapshotRepository =
                mock(MockInterviewInputSnapshotRepository.class);

        TargetRole targetRole = targetRole();
        PersonalEvidenceArtifact resume = artifact(
                UUID.randomUUID(),
                PersonalEvidenceType.RESUME,
                "resume.md",
                "Java 21、Spring Boot、Redis与Agent项目经验"
        );

        when(careerRepository.findTargetRole(OWNER, targetRole.targetRoleId()))
                .thenReturn(Optional.of(targetRole));
        when(evidenceRepository.findVersionForSnapshot(
                OWNER,
                resume.artifactId(),
                resume.artifactVersion()
        )).thenReturn(Optional.of(resume));
        when(snapshotRepository.claim(any(MockInterviewInputSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MockInterviewInputSnapshot snapshot = service(
                careerRepository,
                evidenceRepository,
                snapshotRepository
        ).create(selection(targetRole, resume));

        assertThat(snapshot.ownerId()).isEqualTo(OWNER);
        assertThat(snapshot.targetRoleId()).isEqualTo(targetRole.targetRoleId());
        assertThat(snapshot.skillGapSnapshotId()).isNull();
        assertThat(snapshot.trainingPlanId()).isNull();
        assertThat(snapshot.artifactReferences()).hasSize(1);
        assertThat(snapshot.artifactReferences().getFirst().artifactId())
                .isEqualTo(resume.artifactId());
        assertThat(snapshot.snapshotContextJson()).contains("\"type\":\"RESUME\"");
        assertThat(snapshot.snapshotHash()).matches("[0-9a-f]{64}");

        verify(snapshotRepository).claim(any(MockInterviewInputSnapshot.class));
    }

    @Test
    void shouldGenerateSameHashRegardlessOfArtifactSelectionOrder() {
        MockInterviewInputSnapshotFactory factory = new MockInterviewInputSnapshotFactory(
                JsonMapper.builder().build()
        );
        TargetRole targetRole = targetRole();
        PersonalEvidenceArtifact resume = artifact(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                PersonalEvidenceType.RESUME,
                "resume.md",
                "Java后端与Agent开发经历"
        );
        PersonalEvidenceArtifact project = artifact(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                PersonalEvidenceType.PROJECT,
                "project.md",
                "CareerForge-AI项目架构与可靠性设计"
        );

        MockInterviewInputSnapshot first = factory.create(
                UUID.randomUUID(),
                OWNER,
                targetRole,
                null,
                null,
                List.of(project, resume),
                NOW
        );
        MockInterviewInputSnapshot second = factory.create(
                UUID.randomUUID(),
                OWNER,
                targetRole,
                null,
                null,
                List.of(resume, project),
                NOW.plusSeconds(30)
        );

        assertThat(first.snapshotContextJson()).isEqualTo(second.snapshotContextJson());
        assertThat(first.snapshotHash()).isEqualTo(second.snapshotHash());
        assertThat(first.artifactReferences().getFirst().artifactId())
                .isEqualTo(resume.artifactId());
    }

    @Test
    void shouldRejectStaleTargetRevokedEvidenceAndMissingOwnerScopedEvidence() {
        CareerPlanningRepository careerRepository = mock(CareerPlanningRepository.class);
        PersonalEvidenceArtifactRepository evidenceRepository =
                mock(PersonalEvidenceArtifactRepository.class);
        MockInterviewInputSnapshotRepository snapshotRepository =
                mock(MockInterviewInputSnapshotRepository.class);
        TargetRole targetRole = targetRole();

        PersonalEvidenceArtifact active = artifact(
                UUID.randomUUID(),
                PersonalEvidenceType.RESUME,
                "resume.md",
                "Java项目经历"
        );
        PersonalEvidenceArtifact revoked = active.revoke(NOW);

        when(careerRepository.findTargetRole(OWNER, targetRole.targetRoleId()))
                .thenReturn(Optional.of(targetRole));
        when(evidenceRepository.findVersionForSnapshot(
                OWNER,
                revoked.artifactId(),
                revoked.artifactVersion()
        )).thenReturn(Optional.of(revoked));

        MockInterviewInputSnapshotApplicationService service = service(
                careerRepository,
                evidenceRepository,
                snapshotRepository
        );

        MockInterviewInputSelection staleTarget = new MockInterviewInputSelection(
                targetRole.targetRoleId(),
                2,
                null,
                null,
                null,
                List.of(new MockInterviewInputSelection.ArtifactVersion(
                        active.artifactId(),
                        active.artifactVersion()
                ))
        );
        assertThatThrownBy(() -> service.create(staleTarget))
                .isInstanceOf(MockInterviewInputConflictException.class);

        assertThatThrownBy(() -> service.create(selection(targetRole, revoked)))
                .isInstanceOf(MockInterviewInputConflictException.class);

        UUID missingArtifactId = UUID.randomUUID();
        MockInterviewInputSelection missingEvidence = new MockInterviewInputSelection(
                targetRole.targetRoleId(),
                targetRole.targetRoleVersion(),
                null,
                null,
                null,
                List.of(new MockInterviewInputSelection.ArtifactVersion(missingArtifactId, 1))
        );
        when(evidenceRepository.findVersionForSnapshot(OWNER, missingArtifactId, 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(missingEvidence))
                .isInstanceOf(MockInterviewInputConflictException.class);

        verify(snapshotRepository, never()).claim(any(MockInterviewInputSnapshot.class));
    }

    private MockInterviewInputSnapshotApplicationService service(
            CareerPlanningRepository careerRepository,
            PersonalEvidenceArtifactRepository evidenceRepository,
            MockInterviewInputSnapshotRepository snapshotRepository
    ) {
        return new MockInterviewInputSnapshotApplicationService(
                () -> OWNER,
                careerRepository,
                evidenceRepository,
                snapshotRepository,
                new MockInterviewInputSnapshotFactory(JsonMapper.builder().build()),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private MockInterviewInputSelection selection(
            TargetRole targetRole,
            PersonalEvidenceArtifact artifact
    ) {
        return new MockInterviewInputSelection(
                targetRole.targetRoleId(),
                targetRole.targetRoleVersion(),
                null,
                null,
                null,
                List.of(new MockInterviewInputSelection.ArtifactVersion(
                        artifact.artifactId(),
                        artifact.artifactVersion()
                ))
        );
    }

    private TargetRole targetRole() {
        JobRequirements requirements = new JobRequirements(
                "Java Agent开发工程师",
                List.of("Java"),
                List.of("Spring Boot", "MySQL", "Redis"),
                List.of("Agent工作流编排"),
                List.of("RAG"),
                List.of("可靠性与测试"),
                List.of(),
                List.of("开发可恢复的Agent应用"),
                List.of("Java并发", "Agent状态管理")
        );

        return TargetRole.createConfirmed(
                UUID.randomUUID(),
                OWNER,
                1,
                "java-agent-jd",
                "a".repeat(64),
                "parser-v1",
                "prompt-v1",
                requirements,
                NOW
        );
    }

    private PersonalEvidenceArtifact artifact(
            UUID artifactId,
            PersonalEvidenceType type,
            String sourceName,
            String content
    ) {
        return new PersonalEvidenceArtifactFactory().create(
                artifactId,
                1,
                OWNER,
                type,
                sourceName,
                content,
                NOW
        );
    }
}