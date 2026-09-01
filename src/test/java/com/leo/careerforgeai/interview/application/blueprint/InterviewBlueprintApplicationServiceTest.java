package com.leo.careerforgeai.interview.application.blueprint;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.interview.application.evidence.PersonalEvidenceArtifactFactory;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.interview.domain.round.InterviewBlueprint;
import com.leo.careerforgeai.interview.domain.session.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceType;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionInput;

/**
 * @program: CareerForge-AI
 * @description: 验证从冻结事实生成蓝图时的版本、Hash、撤销和仅简历边界
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
class InterviewBlueprintApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("blueprint-owner");
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TARGET_ROLE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ARTIFACT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final String SNAPSHOT_HASH = "b".repeat(64);

    private MockInterviewSessionRepository sessionRepository;
    private MockInterviewInputSnapshotRepository snapshotRepository;
    private CareerPlanningRepository careerRepository;
    private PersonalEvidenceArtifactRepository evidenceRepository;
    private InterviewRoundRepository roundRepository;
    private InterviewReviewRepository reviewRepository;
    private InterviewBlueprintApplicationService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(MockInterviewSessionRepository.class);
        snapshotRepository = mock(MockInterviewInputSnapshotRepository.class);
        careerRepository = mock(CareerPlanningRepository.class);
        evidenceRepository = mock(PersonalEvidenceArtifactRepository.class);
        roundRepository = mock(InterviewRoundRepository.class);
        reviewRepository = mock(InterviewReviewRepository.class);
        service = new InterviewBlueprintApplicationService(
                () -> OWNER,
                sessionRepository,
                snapshotRepository,
                careerRepository,
                evidenceRepository,
                roundRepository,
                reviewRepository,
                new InterviewBlueprintPlanner()
        );
    }

    @Test
    void shouldRebuildBlueprintFromFrozenProjectEvidence() {
        PersonalEvidenceArtifact project = artifact(PersonalEvidenceType.PROJECT);
        MockInterviewSession session = session(SNAPSHOT_HASH);
        MockInterviewInputSnapshot snapshot = snapshot(SNAPSHOT_HASH, project);
        stubValidFacts(session, snapshot, targetRole(1), project);

        InterviewBlueprint blueprint = service.plan(INTERVIEW_ID);

        assertThat(blueprint.inputSnapshotHash()).isEqualTo(SNAPSHOT_HASH);
        assertThat(blueprint.questionPlans().stream()
                .map(InterviewBlueprint.QuestionPlan::questionType)
                .toList())
                .containsExactly(
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                        InterviewQuestionType.PROJECT_DEEP_DIVE,
                        InterviewQuestionType.SYSTEM_DESIGN
                );
        assertThat(blueprint.questionAt(2).evidencePreferred()).isTrue();
    }

    @Test
    void shouldRejectSnapshotHashAndTargetRoleVersionConflicts() {
        PersonalEvidenceArtifact resume = artifact(PersonalEvidenceType.RESUME);
        MockInterviewSession session = session(SNAPSHOT_HASH);

        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(session));
        when(snapshotRepository.findById(OWNER, SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshot("d".repeat(64), resume)));

        assertThatThrownBy(() -> service.plan(INTERVIEW_ID))
                .isInstanceOf(MockInterviewInputConflictException.class);

        MockInterviewInputSnapshot matchingSnapshot = snapshot(SNAPSHOT_HASH, resume);
        when(snapshotRepository.findById(OWNER, SNAPSHOT_ID))
                .thenReturn(Optional.of(matchingSnapshot));
        when(careerRepository.findTargetRole(OWNER, TARGET_ROLE_ID))
                .thenReturn(Optional.of(targetRole(2)));

        assertThatThrownBy(() -> service.plan(INTERVIEW_ID))
                .isInstanceOf(MockInterviewInputConflictException.class);
    }

    @Test
    void shouldRejectEvidenceRevokedAfterSnapshotCreation() {
        PersonalEvidenceArtifact active = artifact(PersonalEvidenceType.RESUME);
        PersonalEvidenceArtifact revoked = active.revoke(NOW.plusSeconds(1));
        MockInterviewSession session = session(SNAPSHOT_HASH);
        MockInterviewInputSnapshot snapshot = snapshot(SNAPSHOT_HASH, active);
        stubValidFacts(session, snapshot, targetRole(1), revoked);

        assertThatThrownBy(() -> service.plan(INTERVIEW_ID))
                .isInstanceOf(MockInterviewInputConflictException.class);
    }

    @Test
    void shouldPlanResumeOnlyInterviewAndExposeFrozenResumeToInterviewer() {
        PersonalEvidenceArtifact resume = artifact(PersonalEvidenceType.RESUME);
        MockInterviewSession session = session(SNAPSHOT_HASH);
        MockInterviewInputSnapshot snapshot = snapshot(SNAPSHOT_HASH, resume);
        stubValidFacts(session, snapshot, targetRole(1), resume);

        InterviewBlueprint blueprint = service.plan(INTERVIEW_ID);

        assertThat(blueprint.questionPlans().stream()
                .map(InterviewBlueprint.QuestionPlan::questionType)
                .toList())
                .containsExactly(
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                        InterviewQuestionType.SYSTEM_DESIGN,
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE
                );
        assertThat(blueprint.questionPlans())
                .allSatisfy(plan -> {
                    assertThat(plan.questionType())
                            .isNotEqualTo(InterviewQuestionType.PROJECT_DEEP_DIVE);
                    assertThat(plan.evidencePreferred()).isFalse();
                });
        InterviewQuestionInput input = service.prepareFirstQuestion(INTERVIEW_ID);
        PersonalEvidenceArtifact.Chunk resumeChunk = resume.chunks().get(0);

        assertThat(input.interviewId()).isEqualTo(INTERVIEW_ID);
        assertThat(input.roundNo()).isEqualTo(1);
        assertThat(input.questionType()).isEqualTo(InterviewQuestionType.TECHNICAL_KNOWLEDGE);
        assertThat(input.blueprintSummary()).contains("第1题=TECHNICAL_KNOWLEDGE");
        assertThat(input.targetRoleSummary()).contains("Java AI应用开发工程师");
        assertThat(input.evidenceByChunkId())
                .containsEntry(resumeChunk.evidenceChunkId(), resumeChunk.chunkContent());
        assertThat(input.completedQuestionSummaries()).isEmpty();
    }

    private void stubValidFacts(
            MockInterviewSession session,
            MockInterviewInputSnapshot snapshot,
            TargetRole targetRole,
            PersonalEvidenceArtifact artifact
    ) {
        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(session));
        when(snapshotRepository.findById(OWNER, SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshot));
        when(careerRepository.findTargetRole(OWNER, TARGET_ROLE_ID))
                .thenReturn(Optional.of(targetRole));
        when(evidenceRepository.findVersionForSnapshot(
                OWNER,
                artifact.artifactId(),
                artifact.artifactVersion()
        )).thenReturn(Optional.of(artifact));
    }

    private MockInterviewSession session(String snapshotHash) {
        return MockInterviewSession.create(
                INTERVIEW_ID,
                OWNER,
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                "a".repeat(64),
                InterviewMode.TARGETED_MOCK,
                SNAPSHOT_ID,
                snapshotHash,
                new InterviewBudgetPolicy(3, 1, 12, 12_000),
                NOW
        );
    }

    private MockInterviewInputSnapshot snapshot(
            String snapshotHash,
            PersonalEvidenceArtifact artifact
    ) {
        return new MockInterviewInputSnapshot(
                SNAPSHOT_ID,
                OWNER,
                MockInterviewInputSnapshot.CURRENT_SCHEMA_VERSION,
                TARGET_ROLE_ID,
                1,
                null,
                null,
                null,
                "{}",
                List.of(new MockInterviewInputSnapshot.ArtifactReference(
                        artifact.artifactId(),
                        artifact.artifactVersion(),
                        artifact.sourceHash(),
                        1
                )),
                snapshotHash,
                NOW
        );
    }

    private TargetRole targetRole(long version) {
        JobRequirements requirements = new JobRequirements(
                "Java AI应用开发工程师",
                List.of("Java"),
                List.of("MySQL与Redis"),
                List.of("Agent可靠性"),
                List.of("RAG"),
                List.of("自动化测试"),
                List.of(),
                List.of(),
                List.of("Java并发", "Agent工作流")
        );

        return TargetRole.createConfirmed(
                TARGET_ROLE_ID,
                OWNER,
                version,
                "fixed-jd",
                "c".repeat(64),
                "parser-v1",
                "prompt-v1",
                requirements,
                NOW
        );
    }

    private PersonalEvidenceArtifact artifact(PersonalEvidenceType type) {
        return new PersonalEvidenceArtifactFactory().create(
                ARTIFACT_ID,
                1,
                OWNER,
                type,
                type == PersonalEvidenceType.PROJECT ? "项目说明" : "个人简历",
                type == PersonalEvidenceType.PROJECT
                        ? "项目使用Java、MySQL和Redis实现可靠任务处理。"
                        : "候选人具备Java后端开发经验，熟悉并发与数据库。",
                NOW
        );
    }
}