package com.leo.careerforgeai.interviewflow.application.session;

import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository.InputSnapshot;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository.SessionClaim;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskDefinition;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.ComprehensiveInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.ConfirmedProfile;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * @program: CareerForge-AI
 * @description: 管理快速面试会话的幂等创建、owner查询和单活动准入
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Service
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class InterviewSessionApplicationService {

    private static final long INPUT_VERSION = 1;
    private static final long PLAN_VERSION = 1;
    private static final int PREPARATION_MAX_ATTEMPTS = 3;
    private static final QuickInterviewPolicy POLICY =
            new QuickInterviewPolicy();
    private static final InterviewStateMachine STATE_MACHINE =
            new InterviewStateMachine(POLICY);

    private final CurrentActorProvider currentActorProvider;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewInputSnapshotRepository inputSnapshotRepository;
    private final InterviewTaskRepository taskRepository;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    public InterviewSessionApplicationService(
            CurrentActorProvider currentActorProvider,
            InterviewSessionRepository sessionRepository,
            InterviewInputSnapshotRepository inputSnapshotRepository,
            InterviewTaskRepository taskRepository,
            Clock clock
    ) {
        this(currentActorProvider, sessionRepository, inputSnapshotRepository,
                taskRepository, clock, JsonMapper.builder().build());
    }

    @Autowired
    public InterviewSessionApplicationService(
            CurrentActorProvider currentActorProvider,
            InterviewSessionRepository sessionRepository,
            InterviewInputSnapshotRepository inputSnapshotRepository,
            InterviewTaskRepository taskRepository,
            Clock clock,
            JsonMapper jsonMapper
    ) {
        this.currentActorProvider = Objects.requireNonNull(
                currentActorProvider,
                "currentActorProvider不能为null"
        );
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository,
                "sessionRepository不能为null"
        );
        this.inputSnapshotRepository = Objects.requireNonNull(
                inputSnapshotRepository,
                "inputSnapshotRepository不能为null"
        );
        this.taskRepository = Objects.requireNonNull(
                taskRepository,
                "taskRepository不能为null"
        );
        this.clock = Objects.requireNonNull(clock, "clock不能为null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为null");
    }

    @Transactional
    public InterviewSession createComprehensive(CreateComprehensiveInterviewCommand command) {
        Objects.requireNonNull(command, "command不能为null");
        if (!CreateQuickInterviewCommand.AI_AGENT_DEVELOPMENT.equals(command.direction())) {
            throw new IllegalArgumentException("首版仅支持AI Agent开发方向");
        }
        ActorId ownerId = currentActorProvider.currentActor();
        Optional<InterviewAggregate> replay = sessionRepository.findByRequestId(ownerId, command.requestId());
        if (replay.isPresent()) return replay.get().session();
        sessionRepository.findInProgress(ownerId).ifPresent(active -> {
            throw new InterviewInProgressException(recoveryFor(ownerId, active));
        });
        Instant now = clock.instant();
        ComprehensiveInterviewPolicy policy = new ComprehensiveInterviewPolicy(command.durationSeconds());
        InterviewAggregate candidate = InterviewAggregate.createComprehensive(
                UUID.randomUUID(), ownerId, now, policy);
        String fingerprint = comprehensiveFingerprint(command);
        InterviewAggregate claimed = sessionRepository.claim(new SessionClaim(
                command.requestId(), fingerprint, candidate));
        if (!claimed.session().interviewId().equals(candidate.session().interviewId())) {
            return claimed.session();
        }
        inputSnapshotRepository.insert(comprehensiveSnapshot(claimed, command, 1, 0, now));
        enqueuePreparation(claimed, 1, 1, fingerprint, now);
        return claimed.session();
    }

    @Transactional
    public InterviewSession updateSetup(UpdateInterviewSetupCommand command) {
        Objects.requireNonNull(command, "command不能为null");
        ActorId ownerId = currentActorProvider.currentActor();
        InterviewAggregate aggregate = findOwned(ownerId, command.interviewId());
        if (aggregate.session().mode() != InterviewMode.COMPREHENSIVE) {
            throw new IllegalStateException("只有综合面试可以修改综合设置");
        }
        if (aggregate.session().stage() != SessionStage.PREPARING
                && aggregate.session().stage() != SessionStage.READY) {
            throw new IllegalStateException("正式开始后不能修改面试设置");
        }
        if (aggregate.session().version() != command.expectedVersion()) {
            throw new IllegalStateException("面试版本已变化，请刷新后重试");
        }
        InputSnapshot current = inputSnapshotRepository.findBySession(ownerId, command.interviewId())
                .orElseThrow(() -> new IllegalStateException("综合面试缺少输入快照"));
        var versions = sessionRepository.findVersions(ownerId, command.interviewId()).orElseThrow();
        long nextInput = Math.addExact(versions.inputVersion(), 1);
        long nextPlan = Math.addExact(versions.planVersion(), 1);
        Instant now = clock.instant();
        InputSnapshot updated = new InputSnapshot(current.inputSnapshotId(), current.interviewId(),
                ownerId, current.direction(), command.jobDescription(), command.salaryRange(),
                command.personalDescription(), serializeProfile(command.confirmedProfile()),
                command.confirmedProfile() == null ? null : 1, command.difficulty().name(),
                command.durationSeconds(), nextInput, current.version() + 1,
                current.createdAt(), now);
        if (!inputSnapshotRepository.updateIfVersionMatches(ownerId, updated, current.version())) {
            throw new IllegalStateException("输入设置已变化，请刷新后重试");
        }
        if (!sessionRepository.reconfigureBeforeStart(ownerId, command.interviewId(),
                command.expectedVersion(), nextInput, nextPlan, command.durationSeconds())) {
            throw new IllegalStateException("面试状态已变化，请刷新后重试");
        }
        InterviewAggregate refreshed = findOwned(ownerId, command.interviewId());
        String fingerprint = setupFingerprint(updated, nextPlan);
        enqueuePreparation(refreshed, nextInput, nextPlan, fingerprint, now);
        return refreshed.session();
    }

    @Transactional
    public InterviewSession createQuick(
            CreateQuickInterviewCommand command
    ) {
        Objects.requireNonNull(command, "command不能为null");
        ActorId ownerId = currentActorProvider.currentActor();

        Optional<InterviewAggregate> replay =
                sessionRepository.findByRequestId(
                        ownerId,
                        command.requestId()
                );
        if (replay.isPresent()) {
            return replay.get().session();
        }

        sessionRepository.findInProgress(ownerId).ifPresent(active -> {
            throw new InterviewInProgressException(
                    recoveryFor(ownerId, active)
            );
        });

        Instant now = clock.instant();
        InterviewAggregate candidate = InterviewAggregate.createQuick(
                UUID.randomUUID(),
                ownerId,
                now,
                POLICY
        );
        String requestFingerprint = creationFingerprint(command.direction());
        InterviewAggregate claimed = sessionRepository.claim(
                new SessionClaim(
                        command.requestId(),
                        requestFingerprint,
                        candidate
                )
        );
        if (!claimed.session().interviewId().equals(
                candidate.session().interviewId()
        )) {
            return claimed.session();
        }

        persistInputSnapshot(claimed, command.direction(), now);
        enqueuePreparation(claimed, requestFingerprint, now);
        return claimed.session();
    }

    @Transactional(readOnly = true)
    public InterviewSession get(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        ActorId ownerId = currentActorProvider.currentActor();
        return findOwned(ownerId, interviewId).session();
    }

    @Transactional(readOnly = true)
    public Optional<InProgressInterview> findInProgress() {
        ActorId ownerId = currentActorProvider.currentActor();
        return sessionRepository.findInProgress(ownerId)
                .map(active -> recoveryFor(ownerId, active));
    }

    @Transactional
    public FinishResult finish(
            UUID interviewId,
            UUID requestId,
            long expectedVersion
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        Objects.requireNonNull(requestId, "requestId不能为null");
        ActorId ownerId = currentActorProvider.currentActor();
        InterviewAggregate current = findOwned(ownerId, interviewId);
        if (current.session().stage() == SessionStage.GENERATING_REPORT
                || current.session().stage() == SessionStage.COMPLETED) {
            return new FinishResult(current.session(),
                    current.session().stage() == SessionStage.GENERATING_REPORT);
        }
        if (current.session().stage() != SessionStage.INTERVIEWING) {
            throw new IllegalStateException("只有正式进行中的面试可以结束");
        }
        if (current.session().version() != expectedVersion) {
            throw new IllegalStateException("面试版本已变化，请刷新后重试");
        }
        Instant now = clock.instant();
        var transition = stateMachineFor(current).transition(
                current, InterviewEvent.FINISH_REQUESTED, now
        );
        InterviewAggregate updated = transition.aggregate();
        if (!sessionRepository.updateIfVersionMatches(
                ownerId, updated, expectedVersion
        )) {
            throw new IllegalStateException("面试状态已变化，请刷新后重试");
        }
        if (transition.shouldGenerateReport()) {
            String businessKey = "report:" + interviewId + ":finish:" + requestId;
            var versions = sessionRepository.findVersions(ownerId, interviewId).orElseThrow();
            boolean enqueued = taskRepository.enqueue(new TaskDefinition(
                    deterministicUuid(businessKey), interviewId, ownerId,
                    TaskType.GENERATE_REPORT, businessKey,
                    PREPARATION_MAX_ATTEMPTS, versions.inputVersion(), versions.planVersion(),
                    sha256("finish=" + interviewId + "\nversion="
                            + updated.session().version()), now
            ));
            if (!enqueued) {
                throw new IllegalStateException("面试报告任务入队失败");
            }
        }
        return new FinishResult(updated.session(),
                transition.shouldGenerateReport());
    }

    @Transactional
    public InterviewSession enter(
            UUID interviewId,
            UUID requestId,
            long expectedVersion
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        Objects.requireNonNull(requestId, "requestId不能为null");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion不能小于0"
            );
        }

        ActorId ownerId = currentActorProvider.currentActor();
        InterviewAggregate aggregate = findOwned(ownerId, interviewId);
        if (aggregate.session().stage() == SessionStage.INTERVIEWING) {
            return aggregate.session();
        }
        if (aggregate.session().stage() != SessionStage.READY) {
            throw new IllegalStateException(
                    "只有READY状态的面试可以正式开始"
            );
        }
        if (aggregate.session().version() != expectedVersion) {
            throw new IllegalStateException(
                    "面试版本已变化，请刷新后重试"
            );
        }

        sessionRepository.findInProgress(ownerId).ifPresent(active -> {
            throw new InterviewInProgressException(
                    recoveryFor(ownerId, active)
            );
        });

        InterviewAggregate updated = stateMachineFor(aggregate).transition(
                aggregate,
                InterviewEvent.FORMAL_START_REQUESTED,
                clock.instant()
        ).aggregate();
        try {
            if (sessionRepository.updateIfVersionMatches(
                    ownerId,
                    updated,
                    expectedVersion
            )) {
                return updated.session();
            }
        } catch (DataIntegrityViolationException exception) {
            throw activeInterviewConflict(ownerId, exception);
        }

        InterviewAggregate latest = findOwned(ownerId, interviewId);
        if (latest.session().stage() == SessionStage.INTERVIEWING) {
            return latest.session();
        }
        Optional<InterviewSession> active =
                sessionRepository.findInProgress(ownerId);
        if (active.isPresent()) {
            throw new InterviewInProgressException(
                    recoveryFor(ownerId, active.get())
            );
        }
        throw new IllegalStateException(
                "面试状态已变化，请刷新后重试"
        );
    }

    private InterviewAggregate findOwned(
            ActorId ownerId,
            UUID interviewId
    ) {
        return sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new InterviewFlowNotFoundException(
                        interviewId
                ));
    }

    private InProgressInterview recoveryFor(
            ActorId ownerId,
            InterviewSession active
    ) {
        Optional<InterviewAggregate> aggregate = sessionRepository.findById(
                ownerId,
                active.interviewId()
        );
        if (aggregate.isEmpty()) {
            return new InProgressInterview(
                    active.interviewId(),
                    active.mode(),
                    1,
                    POLICY.duration().toSeconds()
            );
        }
        InterviewAggregate stored = aggregate.get();
        long remainingSeconds = stored.timer()
                .remaining(clock.instant())
                .toSeconds();
        return new InProgressInterview(
                active.interviewId(),
                active.mode(),
                Math.addExact(stored.submittedAnswerCount(), 1),
                remainingSeconds
        );
    }

    private InterviewInProgressException activeInterviewConflict(
            ActorId ownerId,
            DataIntegrityViolationException cause
    ) {
        return sessionRepository.findInProgress(ownerId)
                .map(active -> new InterviewInProgressException(
                        recoveryFor(ownerId, active)
                ))
                .orElseThrow(() -> cause);
    }

    private void persistInputSnapshot(
            InterviewAggregate aggregate,
            String direction,
            Instant now
    ) {
        UUID interviewId = aggregate.session().interviewId();
        UUID snapshotId = deterministicUuid("input:" + interviewId);
        inputSnapshotRepository.insert(new InputSnapshot(
                snapshotId,
                interviewId,
                aggregate.session().ownerId(),
                direction,
                null,
                null,
                null,
                null,
                null,
                null,
                POLICY.duration().toSeconds(),
                INPUT_VERSION,
                0,
                now,
                now
        ));
    }

    private void enqueuePreparation(
            InterviewAggregate aggregate,
            String contextFingerprint,
            Instant now
    ) {
        enqueuePreparation(aggregate, INPUT_VERSION, PLAN_VERSION, contextFingerprint, now);
    }

    private void enqueuePreparation(InterviewAggregate aggregate, long inputVersion,
            long planVersion, String contextFingerprint, Instant now) {
        UUID interviewId = aggregate.session().interviewId();
        String businessKey = "prepare:" + interviewId
                + ":input:" + inputVersion
                + ":plan:" + planVersion;
        taskRepository.enqueue(new TaskDefinition(
                deterministicUuid(businessKey),
                interviewId,
                aggregate.session().ownerId(),
                TaskType.PREPARE_INTERVIEW,
                businessKey,
                PREPARATION_MAX_ATTEMPTS,
                inputVersion,
                planVersion,
                contextFingerprint,
                now
        ));
    }

    private static String creationFingerprint(String direction) {
        return sha256("mode=QUICK\ndirection=" + direction
                + "\ndurationSeconds=" + POLICY.duration().toSeconds());
    }

    private InputSnapshot comprehensiveSnapshot(InterviewAggregate aggregate,
            CreateComprehensiveInterviewCommand command, long inputVersion,
            long version, Instant now) {
        return new InputSnapshot(deterministicUuid("input:" + aggregate.session().interviewId()),
                aggregate.session().interviewId(), aggregate.session().ownerId(),
                command.direction(), command.jobDescription(), command.salaryRange(),
                command.personalDescription(), serializeProfile(command.confirmedProfile()),
                command.confirmedProfile() == null ? null : 1,
                command.difficulty().name(), command.durationSeconds(), inputVersion,
                version, now, now);
    }

    private String serializeProfile(ConfirmedProfile profile) {
        if (profile == null) return null;
        try {
            return jsonMapper.writeValueAsString(profile);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("确认画像无法序列化", exception);
        }
    }

    private static String comprehensiveFingerprint(CreateComprehensiveInterviewCommand command) {
        return sha256("mode=COMPREHENSIVE\ndirection=" + command.direction()
                + "\njd=" + command.jobDescription() + "\nsalary=" + command.salaryRange()
                + "\npersonal=" + Objects.toString(command.personalDescription(), "")
                + "\nprofile=" + Objects.toString(command.confirmedProfile(), "")
                + "\nduration=" + command.durationSeconds()
                + "\ndifficulty=" + command.difficulty());
    }

    private static String setupFingerprint(InputSnapshot snapshot, long planVersion) {
        return sha256("input=" + snapshot.inputVersion() + "\nplan=" + planVersion
                + "\njd=" + snapshot.jobDescription() + "\nsalary=" + snapshot.salaryRange()
                + "\nprofile=" + Objects.toString(snapshot.confirmedProfileJson(), "")
                + "\nduration=" + snapshot.durationSeconds()
                + "\ndifficulty=" + snapshot.difficulty());
    }

    private static InterviewStateMachine stateMachineFor(InterviewAggregate aggregate) {
        return aggregate.session().mode() == InterviewMode.COMPREHENSIVE
                ? new InterviewStateMachine(new ComprehensiveInterviewPolicy(
                        aggregate.timer().duration().toSeconds()))
                : STATE_MACHINE;
    }

    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JVM不支持SHA-256",
                    exception
            );
        }
    }

    /**
     * 只暴露跳转回进行中面试所需的最小恢复信息。
     */
    public record InProgressInterview(
            UUID interviewId,
            InterviewMode mode,
            int currentQuestionNumber,
            long remainingSeconds
    ) {

        public InProgressInterview {
            Objects.requireNonNull(interviewId, "interviewId不能为null");
            Objects.requireNonNull(mode, "mode不能为null");
            if (currentQuestionNumber < 1) {
                throw new IllegalArgumentException(
                        "currentQuestionNumber必须大于等于1"
                );
            }
            if (remainingSeconds < 0) {
                throw new IllegalArgumentException(
                        "remainingSeconds不能小于0"
                );
            }
        }
    }

    public record FinishResult(
            InterviewSession session,
            boolean reportGenerating
    ) {
        public FinishResult {
            Objects.requireNonNull(session, "session不能为null");
        }
    }
}
