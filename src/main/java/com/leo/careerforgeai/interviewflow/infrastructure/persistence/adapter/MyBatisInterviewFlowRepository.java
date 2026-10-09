package com.leo.careerforgeai.interviewflow.infrastructure.persistence.adapter;

import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.AnswerRecord;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.FinalDecision;
import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository.InputSnapshot;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionRecord;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionDetails;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionWrite;
import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository.ReportSnapshot;
import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository.ReportStatus;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository.SessionClaim;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository.SessionVersions;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.ClaimedTask;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskDefinition;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTopicRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTopicRepository.TopicRecord;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.timer.InterviewTimer;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;
import com.leo.careerforgeai.interviewflow.domain.topic.InterviewTopic;
import com.leo.careerforgeai.interviewflow.domain.topic.TopicStatus;
import com.leo.careerforgeai.interviewflow.domain.turn.AnswerStatus;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewAnswer;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion;
import com.leo.careerforgeai.interviewflow.domain.turn.QuestionStatus;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowAnswerEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowInputSnapshotEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowQuestionEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowReportEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowSessionEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowTaskEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowTopicEntity;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowAnswerMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowInputSnapshotMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowQuestionMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowReportMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowSessionMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowTaskMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper.InterviewFlowTopicMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 连接新面试领域端口与七表MyBatis持久化实现
 * @author: Miao Zheng
 * @date: 2026-10-03
 **/
@Repository
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class MyBatisInterviewFlowRepository
        implements InterviewSessionRepository,
        InterviewInputSnapshotRepository,
        InterviewTopicRepository,
        InterviewQuestionRepository,
        InterviewAnswerRepository,
        InterviewTaskRepository,
        InterviewReportRepository {

    private static final String QUICK_TOPIC_TYPE = "QUICK_OBJECTIVE";

    private final InterviewFlowSessionMapper sessionMapper;
    private final InterviewFlowInputSnapshotMapper inputSnapshotMapper;
    private final InterviewFlowTopicMapper topicMapper;
    private final InterviewFlowQuestionMapper questionMapper;
    private final InterviewFlowAnswerMapper answerMapper;
    private final InterviewFlowTaskMapper taskMapper;
    private final InterviewFlowReportMapper reportMapper;

    public MyBatisInterviewFlowRepository(
            InterviewFlowSessionMapper sessionMapper,
            InterviewFlowInputSnapshotMapper inputSnapshotMapper,
            InterviewFlowTopicMapper topicMapper,
            InterviewFlowQuestionMapper questionMapper,
            InterviewFlowAnswerMapper answerMapper,
            InterviewFlowTaskMapper taskMapper,
            InterviewFlowReportMapper reportMapper
    ) {
        this.sessionMapper = Objects.requireNonNull(
                sessionMapper,
                "sessionMapper不能为空"
        );
        this.inputSnapshotMapper = Objects.requireNonNull(
                inputSnapshotMapper,
                "inputSnapshotMapper不能为空"
        );
        this.topicMapper = Objects.requireNonNull(
                topicMapper,
                "topicMapper不能为空"
        );
        this.questionMapper = Objects.requireNonNull(
                questionMapper,
                "questionMapper不能为空"
        );
        this.answerMapper = Objects.requireNonNull(
                answerMapper,
                "answerMapper不能为空"
        );
        this.taskMapper = Objects.requireNonNull(
                taskMapper,
                "taskMapper不能为空"
        );
        this.reportMapper = Objects.requireNonNull(
                reportMapper,
                "reportMapper不能为空"
        );
    }

    @Override
    @Transactional
    public InterviewAggregate claim(SessionClaim claim) {
        Objects.requireNonNull(claim, "claim不能为空");
        InterviewAggregate candidate = claim.candidate();
        if (candidate.session().version() != 0) {
            throw new IllegalArgumentException("新面试version必须为0");
        }

        InterviewFlowSessionEntity entity = toSessionEntity(candidate);
        entity.setRequestId(claim.requestId().toString());
        entity.setRequestFingerprint(claim.requestFingerprint());
        requireAtMostOne(
                sessionMapper.claim(entity),
                "面试幂等认领影响了多行数据"
        );

        InterviewFlowSessionEntity stored =
                sessionMapper.selectByOwnerAndRequestId(
                        candidate.session().ownerId().value(),
                        claim.requestId().toString()
                );
        if (stored == null) {
            throw new IllegalStateException(
                    "面试认领后无法按请求身份读取，可能发生interviewId冲突"
            );
        }
        if (!claim.requestFingerprint().equals(
                stored.getRequestFingerprint()
        )) {
            throw new IllegalStateException(
                    "同一requestId不能对应不同的面试创建请求"
            );
        }
        return toAggregate(stored);
    }

    @Override
    public Optional<InterviewAggregate> findById(
            ActorId ownerId,
            UUID interviewId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        return Optional.ofNullable(sessionMapper.selectByOwnerAndId(
                        ownerId.value(),
                        interviewId.toString()
                ))
                .map(MyBatisInterviewFlowRepository::toAggregate);
    }

    @Override
    public Optional<InterviewAggregate> findByRequestId(
            ActorId ownerId,
            UUID requestId
    ) {
        requireOwnerAndId(ownerId, requestId, "requestId");
        return Optional.ofNullable(
                        sessionMapper.selectByOwnerAndRequestId(
                                ownerId.value(),
                                requestId.toString()
                        )
                )
                .map(MyBatisInterviewFlowRepository::toAggregate);
    }

    @Override
    public Optional<InterviewSession> findInProgress(ActorId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        return Optional.ofNullable(
                        sessionMapper.selectInProgressByOwner(ownerId.value())
                )
                .map(MyBatisInterviewFlowRepository::toSession);
    }

    @Override
    public Optional<SessionVersions> findVersions(
            ActorId ownerId,
            UUID interviewId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        return Optional.ofNullable(sessionMapper.selectByOwnerAndId(
                        ownerId.value(),
                        interviewId.toString()
                ))
                .map(entity -> new SessionVersions(
                        requireLong(entity.getInputVersion(), "inputVersion"),
                        requireLong(entity.getPlanVersion(), "planVersion")
                ));
    }

    @Override
    public List<InterviewAggregate> findRunningStartedBefore(
            Instant cutoff,
            int limit
    ) {
        Objects.requireNonNull(cutoff, "cutoff不能为null");
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit必须在1到1000之间");
        }
        return sessionMapper.selectRunningStartedBefore(cutoff, limit)
                .stream()
                .map(MyBatisInterviewFlowRepository::toAggregate)
                .toList();
    }

    @Override
    public List<InterviewAggregate> findPage(
            ActorId ownerId,
            Instant beforeCreatedAt,
            UUID beforeInterviewId,
            int limit
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为null");
        if ((beforeCreatedAt == null) != (beforeInterviewId == null)) {
            throw new IllegalArgumentException("游标必须同时提供时间和面试ID");
        }
        if (limit < 1 || limit > 101) {
            throw new IllegalArgumentException("limit必须在1到101之间");
        }
        return sessionMapper.selectOwnerPage(
                        ownerId.value(),
                        beforeCreatedAt,
                        beforeInterviewId == null ? null : beforeInterviewId.toString(),
                        limit
                ).stream()
                .map(MyBatisInterviewFlowRepository::toAggregate)
                .toList();
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            InterviewAggregate updated,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updated, "updated不能为空");
        if (!ownerId.equals(updated.session().ownerId())) {
            throw new IllegalArgumentException("ownerId与面试归属不一致");
        }
        requireNextVersion(
                expectedVersion,
                updated.session().version(),
                "面试"
        );
        int affectedRows = sessionMapper.updateIfVersionMatches(
                toSessionEntity(updated),
                ownerId.value(),
                expectedVersion
        );
        requireAtMostOne(affectedRows, "面试CAS更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public void insert(InputSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot不能为空");
        requireNewVersion(snapshot.version(), "输入快照");
        requireSingleInsert(
                inputSnapshotMapper.insert(toInputSnapshotEntity(snapshot)),
                "输入快照插入失败"
        );
    }

    @Override
    public Optional<InputSnapshot> findBySession(
            ActorId ownerId,
            UUID interviewId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        return Optional.ofNullable(inputSnapshotMapper.selectBySession(
                        ownerId.value(),
                        interviewId.toString()
                ))
                .map(MyBatisInterviewFlowRepository::toInputSnapshot);
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            InputSnapshot updated,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updated, "updated不能为空");
        if (!ownerId.equals(updated.ownerId())) {
            throw new IllegalArgumentException("ownerId与输入快照归属不一致");
        }
        requireNextVersion(expectedVersion, updated.version(), "输入快照");
        int affectedRows = inputSnapshotMapper.updateIfVersionMatches(
                toInputSnapshotEntity(updated),
                ownerId.value(),
                expectedVersion
        );
        requireAtMostOne(affectedRows, "输入快照CAS更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    @Transactional
    public void insertAll(
            ActorId ownerId,
            UUID interviewId,
            long planVersion,
            List<InterviewTopic> topics
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(topics, "topics不能为空");
        if (planVersion < 1) {
            throw new IllegalArgumentException("planVersion必须大于等于1");
        }
        Instant now = Instant.now();
        for (InterviewTopic topic : topics) {
            requireSingleInsert(
                    topicMapper.insert(toTopicEntity(
                            ownerId,
                            interviewId,
                            planVersion,
                            Objects.requireNonNull(topic, "topic不能为空"),
                            now
                    )),
                    "考查目标插入失败"
            );
        }
    }

    @Override
    public Optional<InterviewTopic> findTopic(
            ActorId ownerId,
            UUID interviewId,
            UUID topicId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(topicId, "topicId不能为空");
        return Optional.ofNullable(topicMapper.selectByOwnerAndId(
                        ownerId.value(),
                        interviewId.toString(),
                        topicId.toString()
                ))
                .map(MyBatisInterviewFlowRepository::toTopic);
    }

    @Override
    public Optional<TopicRecord> findTopicRecord(
            ActorId ownerId,
            UUID interviewId,
            UUID topicId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(topicId, "topicId不能为空");
        return Optional.ofNullable(topicMapper.selectByOwnerAndId(
                        ownerId.value(),
                        interviewId.toString(),
                        topicId.toString()
                ))
                .map(entity -> new TopicRecord(
                        toTopic(entity),
                        requireLong(entity.getVersion(), "version")
                ));
    }

    @Override
    public List<InterviewTopic> findTopics(
            ActorId ownerId,
            UUID interviewId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        return topicMapper.selectBySessionOrder(
                        ownerId.value(),
                        interviewId.toString()
                ).stream()
                .map(MyBatisInterviewFlowRepository::toTopic)
                .toList();
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            UUID interviewId,
            InterviewTopic updated,
            long expectedVersion
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(updated, "updated不能为空");
        requireExpectedVersion(expectedVersion);
        InterviewFlowTopicEntity entity = new InterviewFlowTopicEntity();
        entity.setTopicId(updated.topicId().toString());
        entity.setTopicStatus(updated.status().name());
        int affectedRows = topicMapper.updateIfVersionMatches(
                entity,
                ownerId.value(),
                interviewId.toString(),
                expectedVersion
        );
        requireAtMostOne(affectedRows, "考查目标CAS更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    @Transactional
    public void insertAll(
            ActorId ownerId,
            UUID interviewId,
            List<QuestionWrite> questions
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(questions, "questions不能为空");
        Instant now = Instant.now();
        for (QuestionWrite question : questions) {
            requireSingleInsert(
                    questionMapper.insert(toQuestionEntity(
                            ownerId,
                            interviewId,
                            Objects.requireNonNull(
                                    question,
                                    "question不能为空"
                            ),
                            now
                    )),
                    "面试题插入失败"
            );
        }
    }

    @Override
    public Optional<InterviewQuestion> findQuestion(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(questionId, "questionId不能为空");
        return Optional.ofNullable(questionMapper.selectByOwnerAndId(
                        ownerId.value(),
                        interviewId.toString(),
                        questionId.toString()
                ))
                .map(MyBatisInterviewFlowRepository::toQuestion);
    }

    @Override
    public Optional<QuestionRecord> findQuestionRecord(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(questionId, "questionId不能为null");
        return Optional.ofNullable(questionMapper.selectByOwnerAndId(
                        ownerId.value(),
                        interviewId.toString(),
                        questionId.toString()
                ))
                .map(entity -> new QuestionRecord(
                        toQuestion(entity),
                        requireLong(entity.getVersion(), "version")
                ));
    }

    @Override
    public Optional<QuestionDetails> findQuestionDetails(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(questionId, "questionId不能为空");
        return Optional.ofNullable(questionMapper.selectByOwnerAndId(
                        ownerId.value(),
                        interviewId.toString(),
                        questionId.toString()
                ))
                .map(entity -> new QuestionDetails(
                        toQuestion(entity),
                        parseNullableUuid(
                                entity.getParentQuestionId(),
                                "parentQuestionId"
                        ),
                        requireText(
                                entity.getSourceRefsJson(),
                                "sourceRefsJson"
                        ),
                        requireText(
                                entity.getContentFingerprint(),
                                "contentFingerprint"
                        ),
                        requireLong(entity.getPlanVersion(), "planVersion"),
                        requireLong(entity.getVersion(), "version")
                ));
    }

    @Override
    public List<InterviewQuestion> findQuestions(
            ActorId ownerId,
            UUID interviewId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        return questionMapper.selectBySessionOrder(
                        ownerId.value(),
                        interviewId.toString()
                ).stream()
                .map(MyBatisInterviewFlowRepository::toQuestion)
                .toList();
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            UUID interviewId,
            InterviewQuestion updated,
            long expectedVersion
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(updated, "updated不能为空");
        requireExpectedVersion(expectedVersion);
        InterviewFlowQuestionEntity entity =
                new InterviewFlowQuestionEntity();
        entity.setQuestionId(updated.questionId().toString());
        entity.setQuestionStatus(updated.status().name());
        int affectedRows = questionMapper.updateIfVersionMatches(
                entity,
                ownerId.value(),
                interviewId.toString(),
                expectedVersion
        );
        requireAtMostOne(affectedRows, "面试题CAS更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public void insert(
            ActorId ownerId,
            UUID interviewId,
            AnswerRecord answer
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(answer, "answer不能为空");
        requireNewVersion(answer.version(), "答案");
        requireSingleInsert(
                answerMapper.insert(toAnswerEntity(
                        ownerId,
                        interviewId,
                        answer,
                        Instant.now()
                )),
                "答案插入失败"
        );
    }

    @Override
    public Optional<AnswerRecord> findByQuestion(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(questionId, "questionId不能为空");
        return Optional.ofNullable(answerMapper.selectByQuestion(
                        ownerId.value(),
                        interviewId.toString(),
                        questionId.toString()
                ))
                .map(MyBatisInterviewFlowRepository::toAnswerRecord);
    }

    @Override
    public Optional<AnswerRecord> findAnswerByRequestId(
            ActorId ownerId,
            UUID requestId
    ) {
        requireOwnerAndId(ownerId, requestId, "requestId");
        return Optional.ofNullable(answerMapper.selectByOwnerAndRequestId(
                        ownerId.value(),
                        requestId.toString()
                ))
                .map(MyBatisInterviewFlowRepository::toAnswerRecord);
    }

    @Override
    public Optional<AnswerRecord> findById(
            ActorId ownerId,
            UUID interviewId,
            UUID answerId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(answerId, "answerId不能为空");
        return Optional.ofNullable(answerMapper.selectByOwnerAndId(
                        ownerId.value(),
                        interviewId.toString(),
                        answerId.toString()
                ))
                .map(MyBatisInterviewFlowRepository::toAnswerRecord);
    }

    @Override
    public List<AnswerRecord> findAnswersByInterview(
            ActorId ownerId,
            UUID interviewId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        return answerMapper.selectByInterview(
                        ownerId.value(),
                        interviewId.toString()
                ).stream()
                .map(MyBatisInterviewFlowRepository::toAnswerRecord)
                .toList();
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            UUID interviewId,
            AnswerRecord updated,
            long expectedVersion
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(updated, "updated不能为空");
        requireNextVersion(expectedVersion, updated.version(), "答案");
        int affectedRows = answerMapper.updateIfVersionMatches(
                toAnswerEntity(
                        ownerId,
                        interviewId,
                        updated,
                        Instant.now()
                ),
                ownerId.value(),
                interviewId.toString(),
                expectedVersion
        );
        requireAtMostOne(affectedRows, "答案CAS更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public boolean enqueue(TaskDefinition task) {
        Objects.requireNonNull(task, "task不能为空");
        int affectedRows = taskMapper.enqueue(toTaskEntity(task));
        requireAtMostOne(affectedRows, "任务幂等入队影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    @Transactional
    public Optional<ClaimedTask> claimNextAvailableTask(
            Instant now,
            String leaseOwner,
            Instant leaseUntil
    ) {
        Objects.requireNonNull(now, "now不能为空");
        Objects.requireNonNull(leaseUntil, "leaseUntil不能为空");
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner不能为空");
        }
        if (!leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil必须晚于now");
        }

        int affectedRows = taskMapper.claimNextAvailable(
                now,
                leaseOwner,
                leaseUntil
        );
        requireAtMostOne(affectedRows, "任务原子认领影响了多行数据");
        if (affectedRows == 0) {
            return Optional.empty();
        }
        InterviewFlowTaskEntity claimed =
                taskMapper.selectClaimedByLeaseOwner(leaseOwner);
        if (claimed == null) {
            throw new IllegalStateException("任务认领成功后无法读取租约记录");
        }
        if (!"RUNNING".equals(claimed.getTaskStatus())) {
            throw new IllegalStateException("已认领任务状态必须为RUNNING");
        }
        return Optional.of(toClaimedTask(claimed));
    }

    @Override
    public boolean markSucceeded(ClaimedTask task, Instant completedAt) {
        Objects.requireNonNull(task, "task不能为空");
        Objects.requireNonNull(completedAt, "completedAt不能为空");
        int affectedRows = taskMapper.markSucceeded(
                task.taskId().toString(),
                task.ownerId().value(),
                task.leaseOwner(),
                task.version(),
                completedAt
        );
        requireAtMostOne(affectedRows, "任务完成更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public boolean markRetryWait(
            ClaimedTask task,
            Instant nextRetryAt,
            String errorCode,
            String errorMessage,
            Instant updatedAt
    ) {
        Objects.requireNonNull(task, "task不能为空");
        Objects.requireNonNull(nextRetryAt, "nextRetryAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");
        requireTaskError(errorCode, errorMessage);
        int affectedRows = taskMapper.markRetryWait(
                task.taskId().toString(),
                task.ownerId().value(),
                task.leaseOwner(),
                task.version(),
                nextRetryAt,
                errorCode,
                errorMessage,
                updatedAt
        );
        requireAtMostOne(affectedRows, "任务重试更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public boolean rescheduleUnstarted(
            ClaimedTask task,
            Instant nextRetryAt,
            String errorCode,
            String errorMessage,
            Instant updatedAt
    ) {
        Objects.requireNonNull(task, "task不能为null");
        Objects.requireNonNull(nextRetryAt, "nextRetryAt不能为null");
        Objects.requireNonNull(updatedAt, "updatedAt不能为null");
        requireTaskError(errorCode, errorMessage);
        int affectedRows = taskMapper.rescheduleUnstarted(
                task.taskId().toString(),
                task.ownerId().value(),
                task.leaseOwner(),
                task.version(),
                nextRetryAt,
                errorCode,
                errorMessage,
                updatedAt
        );
        requireAtMostOne(affectedRows, "未执行任务重新排队影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public boolean markFailed(
            ClaimedTask task,
            String errorCode,
            String errorMessage,
            Instant completedAt
    ) {
        Objects.requireNonNull(task, "task不能为空");
        Objects.requireNonNull(completedAt, "completedAt不能为空");
        requireTaskError(errorCode, errorMessage);
        int affectedRows = taskMapper.markFailed(
                task.taskId().toString(),
                task.ownerId().value(),
                task.leaseOwner(),
                task.version(),
                errorCode,
                errorMessage,
                completedAt
        );
        requireAtMostOne(affectedRows, "任务失败更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public void insert(ReportSnapshot report) {
        Objects.requireNonNull(report, "report不能为空");
        requireNewVersion(report.version(), "报告");
        requireSingleInsert(
                reportMapper.insert(toReportEntity(report)),
                "报告插入失败"
        );
    }

    @Override
    public Optional<ReportSnapshot> findByInterview(
            ActorId ownerId,
            UUID interviewId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        return Optional.ofNullable(reportMapper.selectByInterview(
                        ownerId.value(),
                        interviewId.toString()
                ))
                .map(MyBatisInterviewFlowRepository::toReportSnapshot);
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            ReportSnapshot updated,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updated, "updated不能为空");
        if (!ownerId.equals(updated.ownerId())) {
            throw new IllegalArgumentException("ownerId与报告归属不一致");
        }
        requireNextVersion(expectedVersion, updated.version(), "报告");
        int affectedRows = reportMapper.updateIfVersionMatches(
                toReportEntity(updated),
                ownerId.value(),
                expectedVersion
        );
        requireAtMostOne(affectedRows, "报告CAS更新影响了多行数据");
        return affectedRows == 1;
    }

    private static InterviewFlowSessionEntity toSessionEntity(
            InterviewAggregate aggregate
    ) {
        InterviewFlowSessionEntity entity = new InterviewFlowSessionEntity();
        entity.setInterviewId(
                aggregate.session().interviewId().toString()
        );
        entity.setOwnerId(aggregate.session().ownerId().value());
        entity.setInterviewMode(aggregate.session().mode().name());
        entity.setSessionStage(aggregate.session().stage().name());
        entity.setTimerState(aggregate.timer().state().name());
        entity.setDurationSeconds(aggregate.timer().duration().toSeconds());
        entity.setAccumulatedAnswerSeconds(
                aggregate.timer().accumulated().toSeconds()
        );
        entity.setLastActiveAt(aggregate.timer().runningSince());
        entity.setSubmittedAnswerCount(aggregate.submittedAnswerCount());
        entity.setFollowUpCount(aggregate.followUpCount());
        entity.setConsecutiveFollowUpCount(
                aggregate.consecutiveFollowUpCount()
        );
        entity.setInputVersion(1L);
        entity.setPlanVersion(1L);
        entity.setVersion(aggregate.session().version());
        entity.setCreatedAt(aggregate.createdAt());
        entity.setUpdatedAt(aggregate.createdAt());
        return entity;
    }

    private static InterviewAggregate toAggregate(
            InterviewFlowSessionEntity entity
    ) {
        InterviewSession session = toSession(entity);
        InterviewTimer timer = new InterviewTimer(
                Duration.ofSeconds(requireLong(
                        entity.getDurationSeconds(),
                        "durationSeconds"
                )),
                Duration.ofSeconds(requireLong(
                        entity.getAccumulatedAnswerSeconds(),
                        "accumulatedAnswerSeconds"
                )),
                parseEnum(
                        TimerState.class,
                        entity.getTimerState(),
                        "timerState"
                ),
                entity.getLastActiveAt()
        );
        return new InterviewAggregate(
                session,
                timer,
                requireInteger(
                        entity.getSubmittedAnswerCount(),
                        "submittedAnswerCount"
                ),
                requireInteger(entity.getFollowUpCount(), "followUpCount"),
                requireInteger(
                        entity.getConsecutiveFollowUpCount(),
                        "consecutiveFollowUpCount"
                ),
                requireDatabaseValue(entity.getCreatedAt(), "createdAt")
        );
    }

    private static InterviewSession toSession(
            InterviewFlowSessionEntity entity
    ) {
        return new InterviewSession(
                parseUuid(entity.getInterviewId(), "interviewId"),
                new ActorId(requireText(entity.getOwnerId(), "ownerId")),
                parseEnum(
                        InterviewMode.class,
                        entity.getInterviewMode(),
                        "interviewMode"
                ),
                parseEnum(
                        SessionStage.class,
                        entity.getSessionStage(),
                        "sessionStage"
                ),
                requireLong(entity.getVersion(), "version")
        );
    }

    private static InterviewFlowInputSnapshotEntity toInputSnapshotEntity(
            InputSnapshot snapshot
    ) {
        InterviewFlowInputSnapshotEntity entity =
                new InterviewFlowInputSnapshotEntity();
        entity.setInputSnapshotId(snapshot.inputSnapshotId().toString());
        entity.setInterviewId(snapshot.interviewId().toString());
        entity.setOwnerId(snapshot.ownerId().value());
        entity.setInterviewDirection(snapshot.direction());
        entity.setJobDescription(snapshot.jobDescription());
        entity.setSalaryRange(snapshot.salaryRange());
        entity.setPersonalDescription(snapshot.personalDescription());
        entity.setConfirmedProfileJson(snapshot.confirmedProfileJson());
        entity.setProfileSchemaVersion(snapshot.profileSchemaVersion());
        entity.setDifficulty(snapshot.difficulty());
        entity.setDurationSeconds(snapshot.durationSeconds());
        entity.setInputVersion(snapshot.inputVersion());
        entity.setVersion(snapshot.version());
        entity.setCreatedAt(snapshot.createdAt());
        entity.setUpdatedAt(snapshot.updatedAt());
        return entity;
    }

    private static InputSnapshot toInputSnapshot(
            InterviewFlowInputSnapshotEntity entity
    ) {
        return new InputSnapshot(
                parseUuid(entity.getInputSnapshotId(), "inputSnapshotId"),
                parseUuid(entity.getInterviewId(), "interviewId"),
                new ActorId(requireText(entity.getOwnerId(), "ownerId")),
                requireText(
                        entity.getInterviewDirection(),
                        "interviewDirection"
                ),
                entity.getJobDescription(),
                entity.getSalaryRange(),
                entity.getPersonalDescription(),
                entity.getConfirmedProfileJson(),
                entity.getProfileSchemaVersion(),
                entity.getDifficulty(),
                requireLong(entity.getDurationSeconds(), "durationSeconds"),
                requireLong(entity.getInputVersion(), "inputVersion"),
                requireLong(entity.getVersion(), "version"),
                requireDatabaseValue(entity.getCreatedAt(), "createdAt"),
                requireDatabaseValue(entity.getUpdatedAt(), "updatedAt")
        );
    }

    private static InterviewFlowTopicEntity toTopicEntity(
            ActorId ownerId,
            UUID interviewId,
            long planVersion,
            InterviewTopic topic,
            Instant now
    ) {
        InterviewFlowTopicEntity entity = new InterviewFlowTopicEntity();
        entity.setTopicId(topic.topicId().toString());
        entity.setInterviewId(interviewId.toString());
        entity.setOwnerId(ownerId.value());
        entity.setSequenceNo(topic.sequenceNo());
        entity.setTopicType(QUICK_TOPIC_TYPE);
        entity.setObjective(topic.objective());
        entity.setTopicStatus(topic.status().name());
        entity.setSummaryVersion(0L);
        entity.setPlanVersion(planVersion);
        entity.setVersion(0L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private static InterviewTopic toTopic(InterviewFlowTopicEntity entity) {
        return new InterviewTopic(
                parseUuid(entity.getTopicId(), "topicId"),
                requireInteger(entity.getSequenceNo(), "sequenceNo"),
                requireText(entity.getObjective(), "objective"),
                parseEnum(
                        TopicStatus.class,
                        entity.getTopicStatus(),
                        "topicStatus"
                )
        );
    }

    private static InterviewFlowQuestionEntity toQuestionEntity(
            ActorId ownerId,
            UUID interviewId,
            QuestionWrite write,
            Instant now
    ) {
        InterviewQuestion question = write.question();
        InterviewFlowQuestionEntity entity =
                new InterviewFlowQuestionEntity();
        entity.setQuestionId(question.questionId().toString());
        entity.setInterviewId(interviewId.toString());
        entity.setTopicId(question.topicId().toString());
        entity.setOwnerId(ownerId.value());
        entity.setParentQuestionId(toNullableString(
                write.parentQuestionId()
        ));
        entity.setQuestionType(question.type().name());
        entity.setDisplayOrder(question.displayOrder());
        entity.setQuestionText(question.content());
        entity.setSourceRefsJson(write.sourceRefsJson());
        entity.setContentFingerprint(write.contentFingerprint());
        entity.setQuestionStatus(question.status().name());
        entity.setPlanVersion(write.planVersion());
        entity.setVersion(0L);
        setQuestionLifecycleTimes(entity, question.status(), now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private static void setQuestionLifecycleTimes(
            InterviewFlowQuestionEntity entity,
            QuestionStatus status,
            Instant now
    ) {
        if (status == QuestionStatus.PRESENTED) {
            entity.setPresentedAt(now);
        } else if (status == QuestionStatus.CLOSED
                || status == QuestionStatus.SKIPPED) {
            entity.setPresentedAt(now);
            entity.setClosedAt(now);
        }
    }

    private static InterviewQuestion toQuestion(
            InterviewFlowQuestionEntity entity
    ) {
        return new InterviewQuestion(
                parseUuid(entity.getQuestionId(), "questionId"),
                parseUuid(entity.getTopicId(), "topicId"),
                parseEnum(
                        InterviewQuestion.QuestionType.class,
                        entity.getQuestionType(),
                        "questionType"
                ),
                requireInteger(entity.getDisplayOrder(), "displayOrder"),
                requireText(entity.getQuestionText(), "questionText"),
                parseEnum(
                        QuestionStatus.class,
                        entity.getQuestionStatus(),
                        "questionStatus"
                )
        );
    }

    private static InterviewFlowAnswerEntity toAnswerEntity(
            ActorId ownerId,
            UUID interviewId,
            AnswerRecord record,
            Instant now
    ) {
        InterviewAnswer answer = record.answer();
        InterviewFlowAnswerEntity entity = new InterviewFlowAnswerEntity();
        entity.setAnswerId(answer.answerId().toString());
        entity.setInterviewId(interviewId.toString());
        entity.setQuestionId(answer.questionId().toString());
        entity.setOwnerId(ownerId.value());
        entity.setDraftText(answer.draftContent());
        entity.setDraftVersion(0L);
        entity.setFrozenAnswer(answer.submittedContent());
        entity.setAnswerContentHash(record.answerContentHash());
        entity.setSubmitRequestId(toNullableString(record.submitRequestId()));
        entity.setSubmitRequestFingerprint(
                record.submitRequestFingerprint()
        );
        entity.setAnswerStatus(answer.status().name());
        entity.setTurnAnalysisJson(record.turnAnalysisJson());
        entity.setModelDecisionJson(record.modelDecisionJson());
        entity.setFinalDecision(record.finalDecision() == null
                ? null
                : record.finalDecision().name());
        entity.setSubmittedAt(record.submittedAt());
        entity.setVersion(record.version());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private static AnswerRecord toAnswerRecord(
            InterviewFlowAnswerEntity entity
    ) {
        InterviewAnswer answer = new InterviewAnswer(
                parseUuid(entity.getAnswerId(), "answerId"),
                parseUuid(entity.getQuestionId(), "questionId"),
                entity.getDraftText(),
                entity.getFrozenAnswer(),
                parseEnum(
                        AnswerStatus.class,
                        entity.getAnswerStatus(),
                        "answerStatus"
                )
        );
        return new AnswerRecord(
                answer,
                parseNullableUuid(
                        entity.getSubmitRequestId(),
                        "submitRequestId"
                ),
                entity.getSubmitRequestFingerprint(),
                entity.getAnswerContentHash(),
                entity.getTurnAnalysisJson(),
                entity.getModelDecisionJson(),
                parseNullableEnum(
                        FinalDecision.class,
                        entity.getFinalDecision(),
                        "finalDecision"
                ),
                entity.getSubmittedAt(),
                requireLong(entity.getVersion(), "version")
        );
    }

    private static InterviewFlowTaskEntity toTaskEntity(
            TaskDefinition task
    ) {
        InterviewFlowTaskEntity entity = new InterviewFlowTaskEntity();
        entity.setTaskId(task.taskId().toString());
        entity.setInterviewId(task.interviewId().toString());
        entity.setOwnerId(task.ownerId().value());
        entity.setTaskType(task.taskType().name());
        entity.setBusinessKey(task.businessKey());
        entity.setTaskStatus("PENDING");
        entity.setAttemptCount(0);
        entity.setMaxAttempts(task.maxAttempts());
        entity.setInputVersion(task.inputVersion());
        entity.setPlanVersion(task.planVersion());
        entity.setContextFingerprint(task.contextFingerprint());
        entity.setVersion(0L);
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.createdAt());
        return entity;
    }

    private static ClaimedTask toClaimedTask(
            InterviewFlowTaskEntity entity
    ) {
        return new ClaimedTask(
                parseUuid(entity.getTaskId(), "taskId"),
                parseUuid(entity.getInterviewId(), "interviewId"),
                new ActorId(requireText(entity.getOwnerId(), "ownerId")),
                parseEnum(
                        TaskType.class,
                        entity.getTaskType(),
                        "taskType"
                ),
                requireText(entity.getBusinessKey(), "businessKey"),
                requireInteger(entity.getAttemptCount(), "attemptCount"),
                requireInteger(entity.getMaxAttempts(), "maxAttempts"),
                requireLong(entity.getInputVersion(), "inputVersion"),
                requireLong(entity.getPlanVersion(), "planVersion"),
                requireText(
                        entity.getContextFingerprint(),
                        "contextFingerprint"
                ),
                requireLong(entity.getVersion(), "version"),
                requireText(entity.getLeaseOwner(), "leaseOwner"),
                requireDatabaseValue(entity.getLeaseUntil(), "leaseUntil")
        );
    }

    private static InterviewFlowReportEntity toReportEntity(
            ReportSnapshot report
    ) {
        InterviewFlowReportEntity entity = new InterviewFlowReportEntity();
        entity.setReportId(report.reportId().toString());
        entity.setInterviewId(report.interviewId().toString());
        entity.setOwnerId(report.ownerId().value());
        entity.setReportStatus(report.status().name());
        entity.setReportJson(report.reportJson());
        entity.setSchemaVersion(report.schemaVersion());
        entity.setInputVersion(report.inputVersion());
        entity.setPlanVersion(report.planVersion());
        entity.setContextFingerprint(report.contextFingerprint());
        entity.setFailureCode(report.failureCode());
        entity.setVersion(report.version());
        entity.setCreatedAt(report.createdAt());
        entity.setUpdatedAt(report.updatedAt());
        entity.setCompletedAt(report.completedAt());
        return entity;
    }

    private static ReportSnapshot toReportSnapshot(
            InterviewFlowReportEntity entity
    ) {
        return new ReportSnapshot(
                parseUuid(entity.getReportId(), "reportId"),
                parseUuid(entity.getInterviewId(), "interviewId"),
                new ActorId(requireText(entity.getOwnerId(), "ownerId")),
                parseEnum(
                        ReportStatus.class,
                        entity.getReportStatus(),
                        "reportStatus"
                ),
                entity.getReportJson(),
                requireInteger(entity.getSchemaVersion(), "schemaVersion"),
                requireLong(entity.getInputVersion(), "inputVersion"),
                requireLong(entity.getPlanVersion(), "planVersion"),
                requireText(
                        entity.getContextFingerprint(),
                        "contextFingerprint"
                ),
                entity.getFailureCode(),
                requireLong(entity.getVersion(), "version"),
                requireDatabaseValue(entity.getCreatedAt(), "createdAt"),
                requireDatabaseValue(entity.getUpdatedAt(), "updatedAt"),
                entity.getCompletedAt()
        );
    }

    private static void requireOwnerAndId(
            ActorId ownerId,
            UUID id,
            String fieldName
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(id, fieldName + "不能为空");
    }

    private static void requireExpectedVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion不能小于0");
        }
    }

    private static void requireNextVersion(
            long expectedVersion,
            long updatedVersion,
            String resourceName
    ) {
        requireExpectedVersion(expectedVersion);
        if (updatedVersion != expectedVersion + 1) {
            throw new IllegalArgumentException(
                    resourceName + "更新后的version必须比旧version增加1"
            );
        }
    }

    private static void requireNewVersion(
            long version,
            String resourceName
    ) {
        if (version != 0) {
            throw new IllegalArgumentException(
                    resourceName + "初始version必须为0"
            );
        }
    }

    private static void requireTaskError(
            String errorCode,
            String errorMessage
    ) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode不能为空");
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorMessage不能为空");
        }
        if (errorCode.length() > 64 || errorMessage.length() > 500) {
            throw new IllegalArgumentException("任务错误信息超过存储上限");
        }
    }

    private static void requireSingleInsert(
            int affectedRows,
            String message
    ) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    message + ": affectedRows=" + affectedRows
            );
        }
    }

    private static void requireAtMostOne(
            int affectedRows,
            String message
    ) {
        if (affectedRows < 0 || affectedRows > 1) {
            throw new IllegalStateException(
                    message + ": affectedRows=" + affectedRows
            );
        }
    }

    private static int requireInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(
                    "数据库" + fieldName + "不能为空"
            );
        }
        return value;
    }

    private static long requireLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(
                    "数据库" + fieldName + "不能为空"
            );
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "数据库" + fieldName + "不能为空"
            );
        }
        return value;
    }

    private static <T> T requireDatabaseValue(
            T value,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalStateException(
                    "数据库" + fieldName + "不能为空"
            );
        }
        return value;
    }

    private static UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(requireText(value, fieldName));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "数据库" + fieldName + "不是合法UUID",
                    exception
            );
        }
    }

    private static UUID parseNullableUuid(String value, String fieldName) {
        return value == null ? null : parseUuid(value, fieldName);
    }

    private static <E extends Enum<E>> E parseEnum(
            Class<E> enumType,
            String value,
            String fieldName
    ) {
        try {
            return Enum.valueOf(enumType, requireText(value, fieldName));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "数据库" + fieldName + "枚举值不合法",
                    exception
            );
        }
    }

    private static <E extends Enum<E>> E parseNullableEnum(
            Class<E> enumType,
            String value,
            String fieldName
    ) {
        return value == null
                ? null
                : parseEnum(enumType, value, fieldName);
    }

    private static String toNullableString(UUID value) {
        return value == null ? null : value.toString();
    }
}
