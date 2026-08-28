package com.leo.careerforgeai.interview.application.review;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证项目深挖启用证据评审且普通技术题由Java确定性跳过
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewReviewPreparationServiceTest {

    @Test
    void shouldPrepareEvidenceOnlyForProjectDeepDive() {
        ActorId owner = new ActorId("review-owner");
        UUID interviewId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        String snapshotHash = "a".repeat(64);
        String sourceHash = "b".repeat(64);
        String chunkId = "c".repeat(64);

        MockInterviewSessionRepository sessionRepository = mock(MockInterviewSessionRepository.class);
        InterviewRoundRepository roundRepository = mock(InterviewRoundRepository.class);
        MockInterviewInputSnapshotRepository snapshotRepository = mock(MockInterviewInputSnapshotRepository.class);
        PersonalEvidenceArtifactRepository evidenceRepository = mock(PersonalEvidenceArtifactRepository.class);
        MockInterviewSession session = mock(MockInterviewSession.class);
        InterviewRound round = mock(InterviewRound.class);
        InterviewQuestion question = mock(InterviewQuestion.class);
        InterviewAnswer answer = mock(InterviewAnswer.class);
        MockInterviewInputSnapshot snapshot = mock(MockInterviewInputSnapshot.class);
        PersonalEvidenceArtifact artifact = mock(PersonalEvidenceArtifact.class);
        PersonalEvidenceArtifact.Chunk chunk = mock(PersonalEvidenceArtifact.Chunk.class);

        when(sessionRepository.findById(owner, interviewId)).thenReturn(Optional.of(session));
        when(session.ownerId()).thenReturn(owner);
        when(session.inputSnapshotId()).thenReturn(snapshotId);
        when(session.inputSnapshotHash()).thenReturn(snapshotHash);
        when(session.status()).thenReturn(InterviewStatus.REVIEWING);

        when(roundRepository.findRoundByNumber(owner, interviewId, 1)).thenReturn(Optional.of(round));
        when(round.roundId()).thenReturn(roundId);
        when(round.ownerId()).thenReturn(owner);
        when(round.interviewId()).thenReturn(interviewId);
        when(round.status()).thenReturn(InterviewRoundStatus.ANSWERED);

        when(roundRepository.findQuestionByRound(owner, interviewId, roundId))
                .thenReturn(Optional.of(question));
        when(question.questionId()).thenReturn(questionId);
        when(question.roundId()).thenReturn(roundId);
        when(question.interviewId()).thenReturn(interviewId);
        when(question.ownerId()).thenReturn(owner);
        when(question.questionType())
                .thenReturn(InterviewQuestionType.PROJECT_DEEP_DIVE)
                .thenReturn(InterviewQuestionType.TECHNICAL_KNOWLEDGE);
        when(question.questionText()).thenReturn("请说明你在项目中如何处理并发状态竞争。");
        when(question.targetSkills()).thenReturn(List.of("Java并发"));
        when(question.evidenceReferenceIds()).thenReturn(List.of(chunkId));

        when(roundRepository.findAnswerByQuestion(owner, interviewId, questionId))
                .thenReturn(Optional.of(answer));
        when(answer.answerId()).thenReturn(answerId);
        when(answer.questionId()).thenReturn(questionId);
        when(answer.roundId()).thenReturn(roundId);
        when(answer.interviewId()).thenReturn(interviewId);
        when(answer.ownerId()).thenReturn(owner);
        when(answer.answerText()).thenReturn("我使用CAS和唯一约束防止重复状态更新。");

        MockInterviewInputSnapshot.ArtifactReference reference =
                new MockInterviewInputSnapshot.ArtifactReference(
                        artifactId,
                        1,
                        sourceHash,
                        1
                );
        when(snapshotRepository.findById(owner, snapshotId)).thenReturn(Optional.of(snapshot));
        when(snapshot.ownerId()).thenReturn(owner);
        when(snapshot.snapshotHash()).thenReturn(snapshotHash);
        when(snapshot.artifactReferences()).thenReturn(List.of(reference));

        when(evidenceRepository.findVersionForSnapshot(owner, artifactId, 1))
                .thenReturn(Optional.of(artifact));
        when(artifact.ownerId()).thenReturn(owner);
        when(artifact.sourceHash()).thenReturn(sourceHash);
        when(artifact.status()).thenReturn(PersonalEvidenceStatus.ACTIVE);
        when(artifact.chunks()).thenReturn(List.of(chunk));
        when(chunk.evidenceChunkId()).thenReturn(chunkId);
        when(chunk.chunkContent()).thenReturn("项目使用CAS和数据库唯一约束保证状态幂等。");

        InterviewReviewPreparationService service = new InterviewReviewPreparationService(
                () -> owner,
                sessionRepository,
                roundRepository,
                snapshotRepository,
                evidenceRepository
        );

        var projectReview = service.prepare(interviewId, 1, questionId, answerId);
        var technicalReview = service.prepare(interviewId, 1, questionId, answerId);

        assertThat(projectReview.plan())
                .isEqualTo(InterviewReviewPlan.TECHNICAL_AND_EVIDENCE);
        assertThat(projectReview.evidenceInput().evidenceByChunkId())
                .containsEntry(chunkId, "项目使用CAS和数据库唯一约束保证状态幂等。");
        assertThat(technicalReview.plan())
                .isEqualTo(InterviewReviewPlan.TECHNICAL_ONLY);
        assertThat(technicalReview.evidenceInput().evidenceByChunkId()).isEmpty();
    }
}