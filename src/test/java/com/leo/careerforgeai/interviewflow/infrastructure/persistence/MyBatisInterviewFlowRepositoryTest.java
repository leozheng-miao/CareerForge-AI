package com.leo.careerforgeai.interviewflow.infrastructure.persistence;

import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.timer.InterviewTimer;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.adapter.MyBatisInterviewFlowRepository;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowQuestionEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowSessionEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowTaskEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowAnswerMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowInputSnapshotMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowQuestionMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowReportMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowSessionMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowTaskMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowTopicMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证新面试MyBatis适配器的owner、CAS、稳定顺序和任务租约边界
 * @author: Miao Zheng
 * @date: 2026-09-30
 */
class MyBatisInterviewFlowRepositoryTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final ActorId ACTOR_B = new ActorId("actor-b");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TOPIC_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID QUESTION_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-09-30T04:00:00Z");

    private InterviewFlowSessionMapper sessionMapper;
    private InterviewFlowInputSnapshotMapper inputSnapshotMapper;
    private InterviewFlowTopicMapper topicMapper;
    private InterviewFlowQuestionMapper questionMapper;
    private InterviewFlowAnswerMapper answerMapper;
    private InterviewFlowTaskMapper taskMapper;
    private InterviewFlowReportMapper reportMapper;
    private MyBatisInterviewFlowRepository repository;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(InterviewFlowSessionMapper.class);
        inputSnapshotMapper = mock(InterviewFlowInputSnapshotMapper.class);
        topicMapper = mock(InterviewFlowTopicMapper.class);
        questionMapper = mock(InterviewFlowQuestionMapper.class);
        answerMapper = mock(InterviewFlowAnswerMapper.class);
        taskMapper = mock(InterviewFlowTaskMapper.class);
        reportMapper = mock(InterviewFlowReportMapper.class);

        repository = new MyBatisInterviewFlowRepository(
                sessionMapper,
                inputSnapshotMapper,
                topicMapper,
                questionMapper,
                answerMapper,
                taskMapper,
                reportMapper
        );
    }

    @Test
    void shouldScopeSessionReadByOwnerAndInterviewId() {
        InterviewFlowSessionEntity entity = sessionEntity();

        when(sessionMapper.selectByOwnerAndId(
                ACTOR_A.value(),
                INTERVIEW_ID.toString()
        )).thenReturn(entity);

        Optional<InterviewAggregate> result = repository.findById(
                ACTOR_A,
                INTERVIEW_ID
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().session().ownerId())
                .isEqualTo(ACTOR_A);
        verify(sessionMapper).selectByOwnerAndId(
                ACTOR_A.value(),
                INTERVIEW_ID.toString()
        );
    }

    @Test
    void shouldFindOnlyCurrentOwnersInProgressSession() {
        InterviewFlowSessionEntity entity = sessionEntity();

        when(sessionMapper.selectInProgressByOwner(ACTOR_A.value()))
                .thenReturn(entity);

        Optional<InterviewSession> result = repository.findInProgress(ACTOR_A);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().ownerId()).isEqualTo(ACTOR_A);
        verify(sessionMapper).selectInProgressByOwner(ACTOR_A.value());
    }

    @Test
    void shouldRejectOwnerMismatchBeforeSessionCasUpdate() {
        InterviewAggregate updated = readyAggregate();

        assertThatThrownBy(() -> repository.updateIfVersionMatches(
                ACTOR_B,
                updated,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ownerId与面试归属不一致");

        verifyNoInteractions(sessionMapper);
    }

    @Test
    void shouldPassOwnerAndExpectedVersionToSessionCasUpdate() {
        InterviewAggregate updated = readyAggregate();
        when(sessionMapper.updateIfVersionMatches(
                any(InterviewFlowSessionEntity.class),
                eq(ACTOR_A.value()),
                eq(0L)
        )).thenReturn(1);

        boolean result = repository.updateIfVersionMatches(
                ACTOR_A,
                updated,
                0
        );

        assertThat(result).isTrue();
        verify(sessionMapper).updateIfVersionMatches(
                any(InterviewFlowSessionEntity.class),
                eq(ACTOR_A.value()),
                eq(0L)
        );
    }

    @Test
    void shouldReturnFalseWhenSessionCasVersionIsStale() {
        InterviewAggregate updated = readyAggregate();
        when(sessionMapper.updateIfVersionMatches(
                any(InterviewFlowSessionEntity.class),
                eq(ACTOR_A.value()),
                eq(0L)
        )).thenReturn(0);

        boolean result = repository.updateIfVersionMatches(
                ACTOR_A,
                updated,
                0
        );

        assertThat(result).isFalse();
    }

    @Test
    void shouldLoadQuestionsUsingStableDisplayOrderQuery() {
        InterviewFlowQuestionEntity first = questionEntity(
                QUESTION_ID,
                1,
                "请说明受限Agent为什么不能直接修改业务状态"
        );
        InterviewFlowQuestionEntity second = questionEntity(
                UUID.fromString("30000000-0000-0000-0000-000000000002"),
                2,
                "请说明MySQL与Redis在该流程中的职责边界"
        );
        when(questionMapper.selectBySessionOrder(
                ACTOR_A.value(),
                INTERVIEW_ID.toString()
        )).thenReturn(List.of(first, second));

        List<InterviewQuestion> result = repository.findQuestions(
                ACTOR_A,
                INTERVIEW_ID
        );

        assertThat(result)
                .extracting(InterviewQuestion::displayOrder)
                .containsExactly(1, 2);
        verify(questionMapper).selectBySessionOrder(
                ACTOR_A.value(),
                INTERVIEW_ID.toString()
        );
    }

    @Test
    void shouldAtomicallyClaimPendingOrExpiredTaskWithBoundedLease() {
        String leaseOwner = "worker-1";
        Instant leaseUntil = NOW.plusSeconds(30);
        InterviewFlowTaskEntity entity = taskEntity(
                leaseOwner,
                leaseUntil
        );

        when(taskMapper.claimNextAvailable(
                NOW,
                leaseOwner,
                leaseUntil
        )).thenReturn(1);
        when(taskMapper.selectClaimedByLeaseOwner(leaseOwner))
                .thenReturn(entity);

        Optional<InterviewTaskRepository.ClaimedTask> result =
                repository.claimNextAvailableTask(
                        NOW,
                        leaseOwner,
                        leaseUntil
                );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().taskId()).isEqualTo(TASK_ID);
        assertThat(result.orElseThrow().leaseOwner()).isEqualTo(leaseOwner);
        assertThat(result.orElseThrow().leaseUntil()).isEqualTo(leaseUntil);
        verify(taskMapper).claimNextAvailable(
                NOW,
                leaseOwner,
                leaseUntil
        );
        verify(taskMapper).selectClaimedByLeaseOwner(leaseOwner);
    }

    @Test
    void shouldNotReadClaimedTaskWhenAtomicClaimLosesRace() {
        String leaseOwner = "worker-lost-race";
        Instant leaseUntil = NOW.plusSeconds(30);
        when(taskMapper.claimNextAvailable(
                NOW,
                leaseOwner,
                leaseUntil
        )).thenReturn(0);

        Optional<InterviewTaskRepository.ClaimedTask> result =
                repository.claimNextAvailableTask(
                        NOW,
                        leaseOwner,
                        leaseUntil
                );

        assertThat(result).isEmpty();
        verify(taskMapper, never()).selectClaimedByLeaseOwner(leaseOwner);
    }

    @Test
    void shouldRescheduleUnstartedTaskWithoutConsumingAttempt() {
        Instant leaseUntil = NOW.plusSeconds(30);
        InterviewTaskRepository.ClaimedTask task =
                new InterviewTaskRepository.ClaimedTask(
                        TASK_ID,
                        INTERVIEW_ID,
                        ACTOR_A,
                        InterviewTaskRepository.TaskType.GENERATE_REPORT,
                        "report:" + INTERVIEW_ID,
                        3,
                        3,
                        1,
                        1,
                        "a".repeat(64),
                        3,
                        "worker-capacity",
                        leaseUntil
                );
        Instant nextRetryAt = NOW.plusSeconds(5);
        when(taskMapper.rescheduleUnstarted(
                TASK_ID.toString(),
                ACTOR_A.value(),
                "worker-capacity",
                3,
                nextRetryAt,
                "CAPACITY_REJECTED",
                "面试任务执行容量已满",
                NOW
        )).thenReturn(1);

        boolean result = repository.rescheduleUnstarted(
                task,
                nextRetryAt,
                "CAPACITY_REJECTED",
                "面试任务执行容量已满",
                NOW
        );

        assertThat(result).isTrue();
        verify(taskMapper).rescheduleUnstarted(
                TASK_ID.toString(),
                ACTOR_A.value(),
                "worker-capacity",
                3,
                nextRetryAt,
                "CAPACITY_REJECTED",
                "面试任务执行容量已满",
                NOW
        );
    }

    private InterviewAggregate readyAggregate() {
        return new InterviewAggregate(
                new InterviewSession(
                        INTERVIEW_ID,
                        ACTOR_A,
                        InterviewMode.QUICK,
                        SessionStage.READY,
                        1
                ),
                InterviewTimer.notStarted(Duration.ofMinutes(60)),
                0,
                0,
                0,
                NOW
        );
    }

    private InterviewFlowSessionEntity sessionEntity() {
        InterviewFlowSessionEntity entity =
                mock(InterviewFlowSessionEntity.class);
        when(entity.getInterviewId()).thenReturn(INTERVIEW_ID.toString());
        when(entity.getOwnerId()).thenReturn(ACTOR_A.value());
        when(entity.getInterviewMode()).thenReturn(InterviewMode.QUICK.name());
        when(entity.getSessionStage()).thenReturn(SessionStage.READY.name());
        when(entity.getTimerState()).thenReturn(TimerState.NOT_STARTED.name());
        when(entity.getDurationSeconds()).thenReturn(3600L);
        when(entity.getAccumulatedAnswerSeconds()).thenReturn(0L);
        when(entity.getSubmittedAnswerCount()).thenReturn(0);
        when(entity.getFollowUpCount()).thenReturn(0);
        when(entity.getConsecutiveFollowUpCount()).thenReturn(0);
        when(entity.getVersion()).thenReturn(1L);
        when(entity.getCreatedAt()).thenReturn(NOW);
        return entity;
    }

    private InterviewFlowQuestionEntity questionEntity(
            UUID questionId,
            int displayOrder,
            String content
    ) {
        InterviewFlowQuestionEntity entity =
                mock(InterviewFlowQuestionEntity.class);
        when(entity.getQuestionId()).thenReturn(questionId.toString());
        when(entity.getTopicId()).thenReturn(TOPIC_ID.toString());
        when(entity.getQuestionType()).thenReturn(
                InterviewQuestion.QuestionType.MAIN.name()
        );
        when(entity.getDisplayOrder()).thenReturn(displayOrder);
        when(entity.getQuestionText()).thenReturn(content);
        when(entity.getQuestionStatus()).thenReturn("RESERVED");
        return entity;
    }

    private InterviewFlowTaskEntity taskEntity(
            String leaseOwner,
            Instant leaseUntil
    ) {
        InterviewFlowTaskEntity entity =
                mock(InterviewFlowTaskEntity.class);
        when(entity.getTaskId()).thenReturn(TASK_ID.toString());
        when(entity.getInterviewId()).thenReturn(INTERVIEW_ID.toString());
        when(entity.getOwnerId()).thenReturn(ACTOR_A.value());
        when(entity.getTaskType()).thenReturn("PREPARE_INTERVIEW");
        when(entity.getBusinessKey()).thenReturn(
                "prepare:" + INTERVIEW_ID
        );
        when(entity.getTaskStatus()).thenReturn("RUNNING");
        when(entity.getAttemptCount()).thenReturn(1);
        when(entity.getMaxAttempts()).thenReturn(3);
        when(entity.getInputVersion()).thenReturn(1L);
        when(entity.getPlanVersion()).thenReturn(1L);
        when(entity.getContextFingerprint()).thenReturn("a".repeat(64));
        when(entity.getVersion()).thenReturn(1L);
        when(entity.getLeaseOwner()).thenReturn(leaseOwner);
        when(entity.getLeaseUntil()).thenReturn(leaseUntil);
        return entity;
    }
}
