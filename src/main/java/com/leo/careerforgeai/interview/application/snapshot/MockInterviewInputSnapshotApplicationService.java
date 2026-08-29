package com.leo.careerforgeai.interview.application.snapshot;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.domain.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 按当前owner加载并冻结目标岗位、可选Gap、可选训练计划和个人证据版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Service
@ConditionalOnBean({
        CareerPlanningRepository.class,
        PersonalEvidenceArtifactRepository.class,
        MockInterviewInputSnapshotRepository.class
})
public class MockInterviewInputSnapshotApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CareerPlanningRepository careerRepository;
    private final PersonalEvidenceArtifactRepository evidenceRepository;
    private final MockInterviewInputSnapshotRepository snapshotRepository;
    private final MockInterviewInputSnapshotFactory factory;
    private final Clock clock;

    public MockInterviewInputSnapshotApplicationService(
            CurrentActorProvider currentActorProvider,
            CareerPlanningRepository careerRepository,
            PersonalEvidenceArtifactRepository evidenceRepository,
            MockInterviewInputSnapshotRepository snapshotRepository,
            MockInterviewInputSnapshotFactory factory,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.careerRepository = Objects.requireNonNull(careerRepository, "careerRepository不能为空");
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository不能为空");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository不能为空");
        this.factory = Objects.requireNonNull(factory, "factory不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public MockInterviewInputSnapshot create(MockInterviewInputSelection selection) {
        Objects.requireNonNull(selection, "selection不能为空");
        ActorId ownerId = currentActor();

        TargetRole targetRole = careerRepository.findTargetRole(ownerId, selection.targetRoleId())
                .filter(role -> role.targetRoleVersion() == selection.targetRoleVersion())
                .orElseThrow(MockInterviewInputConflictException::new);

        SkillGapSnapshot skillGapSnapshot = loadSkillGap(ownerId, selection);
        TrainingPlan trainingPlan = loadTrainingPlan(ownerId, selection);
        List<PersonalEvidenceArtifact> artifacts = selection.artifactVersions().stream()
                .map(version -> loadArtifact(ownerId, version))
                .toList();

        MockInterviewInputSnapshot candidate = factory.create(
                UUID.randomUUID(),
                ownerId,
                targetRole,
                skillGapSnapshot,
                trainingPlan,
                artifacts,
                clock.instant()
        );
        MockInterviewInputSnapshot stored = snapshotRepository.claim(candidate);

        if (!ownerId.equals(stored.ownerId())
                || !candidate.snapshotHash().equals(stored.snapshotHash())) {
            throw new IllegalStateException("输入快照幂等认领结果违反owner或Hash边界");
        }
        return stored;
    }

    private SkillGapSnapshot loadSkillGap(
            ActorId ownerId,
            MockInterviewInputSelection selection
    ) {
        if (selection.skillGapSnapshotId() == null) return null;
        return careerRepository.findSkillGapSnapshot(ownerId, selection.skillGapSnapshotId())
                .orElseThrow(MockInterviewInputConflictException::new);
    }

    private TrainingPlan loadTrainingPlan(
            ActorId ownerId,
            MockInterviewInputSelection selection
    ) {
        if (selection.trainingPlanId() == null) return null;

        return careerRepository.findTrainingPlan(ownerId, selection.trainingPlanId())
                .filter(plan -> plan.planVersion() == selection.trainingPlanVersion())
                .orElseThrow(MockInterviewInputConflictException::new);
    }

    private PersonalEvidenceArtifact loadArtifact(
            ActorId ownerId,
            MockInterviewInputSelection.ArtifactVersion selectedVersion
    ) {
        PersonalEvidenceArtifact artifact = evidenceRepository.findVersionForSnapshot(
                ownerId,
                selectedVersion.artifactId(),
                selectedVersion.artifactVersion()
        ).orElseThrow(MockInterviewInputConflictException::new);

        if (artifact.status() == PersonalEvidenceStatus.REVOKED) {
            throw new MockInterviewInputConflictException();
        }
        return artifact;
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}