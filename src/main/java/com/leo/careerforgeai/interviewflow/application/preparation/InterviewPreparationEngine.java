package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository.InputSnapshot;
import com.leo.careerforgeai.interviewflow.application.port.InterviewModelPort;
import com.leo.careerforgeai.interviewflow.application.port.InterviewModelPort.GeneratedQuestion;
import com.leo.careerforgeai.interviewflow.application.port.InterviewModelPort.QuestionGenerationRequest;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.KnowledgeQuestionCandidate;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.SourceReference;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionWrite;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTopicRepository;
import com.leo.careerforgeai.interviewflow.application.preparation.QuickInterviewBlueprintPlanner.PlannedTarget;
import com.leo.careerforgeai.interviewflow.application.preparation.QuickInterviewBlueprintPlanner.QuickInterviewBlueprint;
import com.leo.careerforgeai.interviewflow.application.preparation.ComprehensiveInterviewBlueprintPlanner.Blueprint;
import com.leo.careerforgeai.interviewflow.application.preparation.ComprehensiveInterviewBlueprintPlanner.PlannedTheme;
import com.leo.careerforgeai.interviewflow.application.session.CreateQuickInterviewCommand;
import com.leo.careerforgeai.interviewflow.application.session.InterviewFlowNotFoundException;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.ComprehensiveInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion;
import com.leo.careerforgeai.interviewflow.domain.turn.QuestionStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 原子准备快速面试蓝图、带来源主问题和READY状态
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Service
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class InterviewPreparationEngine {

    private static final long DEFAULT_PLAN_VERSION = 1;
    private static final int KNOWLEDGE_CANDIDATE_LIMIT = 3;
    private static final int MIN_QUESTION_LENGTH = 20;
    private static final int MAX_QUESTION_LENGTH = 2_000;
    private static final InterviewStateMachine STATE_MACHINE =
            new InterviewStateMachine(new QuickInterviewPolicy());

    private final CurrentActorProvider currentActorProvider;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewInputSnapshotRepository inputSnapshotRepository;
    private final InterviewTopicRepository topicRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewQuestionKnowledgePort knowledgePort;
    private final InterviewModelPort modelPort;
    private final QuickInterviewBlueprintPlanner blueprintPlanner;
    private final ComprehensivePreparationCoordinator comprehensiveCoordinator;
    private final ComprehensiveInterviewBlueprintPlanner comprehensivePlanner;
    private final JsonMapper jsonMapper;

    public InterviewPreparationEngine(
            CurrentActorProvider currentActorProvider,
            InterviewSessionRepository sessionRepository,
            InterviewInputSnapshotRepository inputSnapshotRepository,
            InterviewTopicRepository topicRepository,
            InterviewQuestionRepository questionRepository,
            InterviewQuestionKnowledgePort knowledgePort,
            InterviewModelPort modelPort,
            QuickInterviewBlueprintPlanner blueprintPlanner,
            JsonMapper jsonMapper
    ) {
        this(currentActorProvider, sessionRepository, inputSnapshotRepository,
                topicRepository, questionRepository, knowledgePort, modelPort,
                blueprintPlanner, null, null, jsonMapper);
    }

    @Autowired
    public InterviewPreparationEngine(
            CurrentActorProvider currentActorProvider,
            InterviewSessionRepository sessionRepository,
            InterviewInputSnapshotRepository inputSnapshotRepository,
            InterviewTopicRepository topicRepository,
            InterviewQuestionRepository questionRepository,
            InterviewQuestionKnowledgePort knowledgePort,
            InterviewModelPort modelPort,
            QuickInterviewBlueprintPlanner blueprintPlanner,
            ComprehensivePreparationCoordinator comprehensiveCoordinator,
            ComprehensiveInterviewBlueprintPlanner comprehensivePlanner,
            JsonMapper jsonMapper
    ) {
        this.currentActorProvider = Objects.requireNonNull(
                currentActorProvider,
                "currentActorProvider不能为空"
        );
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository,
                "sessionRepository不能为空"
        );
        this.inputSnapshotRepository = Objects.requireNonNull(
                inputSnapshotRepository,
                "inputSnapshotRepository不能为空"
        );
        this.topicRepository = Objects.requireNonNull(
                topicRepository,
                "topicRepository不能为空"
        );
        this.questionRepository = Objects.requireNonNull(
                questionRepository,
                "questionRepository不能为空"
        );
        this.knowledgePort = Objects.requireNonNull(
                knowledgePort,
                "knowledgePort不能为空"
        );
        this.modelPort = Objects.requireNonNull(
                modelPort,
                "modelPort不能为空"
        );
        this.blueprintPlanner = Objects.requireNonNull(
                blueprintPlanner,
                "blueprintPlanner不能为空"
        );
        this.comprehensiveCoordinator = comprehensiveCoordinator;
        this.comprehensivePlanner = comprehensivePlanner;
        this.jsonMapper = Objects.requireNonNull(
                jsonMapper,
                "jsonMapper不能为空"
        );
    }

    @Transactional
    public InterviewPreparationResult prepareComprehensive(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        ActorId ownerId = currentActorProvider.currentActor();
        InputSnapshot snapshot = findSnapshot(ownerId, interviewId);
        var versions = sessionRepository.findVersions(ownerId, interviewId).orElseThrow();
        return prepareComprehensive(ownerId, interviewId, snapshot.inputVersion(),
                versions.planVersion());
    }

    @Transactional
    public InterviewPreparationResult prepareComprehensive(ActorId ownerId,
            UUID interviewId, long expectedInputVersion, long planVersion) {
        if (comprehensiveCoordinator == null || comprehensivePlanner == null) {
            throw new IllegalStateException("综合面试准备组件未配置");
        }
        InterviewAggregate aggregate = findInterview(ownerId, interviewId);
        if (aggregate.session().mode() != InterviewMode.COMPREHENSIVE) {
            throw new IllegalStateException("该准备入口只支持综合面试");
        }
        if (aggregate.session().stage() == SessionStage.READY) {
            return existingComprehensiveReadyResult(ownerId, aggregate, planVersion);
        }
        if (aggregate.session().stage() != SessionStage.PREPARING) {
            throw new IllegalStateException("只有PREPARING状态的综合面试可以执行准备");
        }
        InputSnapshot snapshot = findSnapshot(ownerId, interviewId);
        var versions = sessionRepository.findVersions(ownerId, interviewId)
                .orElseThrow(() -> new InterviewFlowNotFoundException(interviewId));
        if (snapshot.inputVersion() != expectedInputVersion
                || versions.inputVersion() != expectedInputVersion
                || versions.planVersion() != planVersion) {
            throw new IllegalStateException("准备任务输入版本已经过期");
        }

        Blueprint blueprint = comprehensivePlanner.plan(interviewId, planVersion,
                comprehensiveCoordinator.collect(snapshot));
        List<QuestionWrite> writes = prepareComprehensiveQuestions(blueprint, planVersion);
        topicRepository.insertAll(ownerId, interviewId, planVersion,
                blueprint.topics().stream()
                        .map(topic -> topic.sequenceNo() == 1 ? topic.activate() : topic)
                        .toList());
        questionRepository.insertAll(ownerId, interviewId, writes);

        InterviewAggregate ready = new InterviewStateMachine(
                new ComprehensiveInterviewPolicy(snapshot.durationSeconds()))
                .transition(aggregate, InterviewEvent.PREPARATION_COMPLETED).aggregate();
        if (!sessionRepository.updateIfVersionMatches(ownerId, ready,
                aggregate.session().version())) {
            throw new IllegalStateException("面试状态已变化，准备结果不能发布");
        }
        return new InterviewPreparationResult(interviewId, SessionStage.READY,
                writes.size(), ready.session().version());
    }

    private List<QuestionWrite> prepareComprehensiveQuestions(Blueprint blueprint,
            long planVersion) {
        List<QuestionWrite> writes = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        for (PlannedTheme theme : blueprint.themes()) {
            PreparedQuestion prepared = qualified(theme.question(), theme.sources(), fingerprints);
            if (prepared == null) {
                throw new IllegalStateException("综合面试主题题目不合格或重复");
            }
            InterviewQuestion question = InterviewQuestion.reserved(
                    deterministicUuid("comprehensive-question:" + theme.topic().topicId()),
                    theme.topic().topicId(), InterviewQuestion.QuestionType.MAIN,
                    theme.topic().sequenceNo() * 10, prepared.content());
            if (theme.topic().sequenceNo() == 1) question = question.present();
            writes.add(new QuestionWrite(question, null,
                    serializeSources(prepared.sources()), prepared.fingerprint(), planVersion));
        }
        if (writes.size() != ComprehensiveInterviewPolicyPack.REQUIRED_THEME_COUNT) {
            throw new IllegalStateException("综合面试未准备完整的六主题题目");
        }
        return List.copyOf(writes);
    }

    private InterviewPreparationResult existingComprehensiveReadyResult(ActorId ownerId,
            InterviewAggregate aggregate, long planVersion) {
        List<InterviewQuestion> current = questionRepository.findQuestions(ownerId,
                aggregate.session().interviewId()).stream()
                .filter(question -> question.status() != QuestionStatus.INVALIDATED)
                .toList();
        if (current.size() != ComprehensiveInterviewPolicyPack.REQUIRED_THEME_COUNT) {
            throw new IllegalStateException("READY综合面试缺少完整六主题准备结果");
        }
        return new InterviewPreparationResult(aggregate.session().interviewId(),
                SessionStage.READY, current.size(), aggregate.session().version());
    }

    @Transactional
    public InterviewPreparationResult prepareQuick(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        ActorId ownerId = currentActorProvider.currentActor();
        InputSnapshot snapshot = findSnapshot(ownerId, interviewId);
        return prepareQuick(
                ownerId,
                interviewId,
                snapshot.inputVersion(),
                DEFAULT_PLAN_VERSION
        );
    }

    /**
     * 后台任务入口显式携带owner和版本，禁止依赖模型提供身份。
     */
    @Transactional
    public InterviewPreparationResult prepareQuick(
            ActorId ownerId,
            UUID interviewId,
            long expectedInputVersion,
            long planVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        if (expectedInputVersion < 1 || planVersion < 1) {
            throw new IllegalArgumentException("准备任务版本必须大于等于1");
        }

        InterviewAggregate aggregate = findInterview(ownerId, interviewId);
        requireQuickInterview(aggregate);
        if (aggregate.session().stage() == SessionStage.READY) {
            return existingReadyResult(ownerId, aggregate);
        }
        if (aggregate.session().stage() != SessionStage.PREPARING) {
            throw new IllegalStateException(
                    "只有PREPARING状态的快速面试可以执行准备"
            );
        }

        InputSnapshot snapshot = findSnapshot(ownerId, interviewId);
        var currentVersions = sessionRepository.findVersions(
                        ownerId,
                        interviewId
                )
                .orElseThrow(() -> new InterviewFlowNotFoundException(
                        interviewId
                ));
        if (snapshot.inputVersion() != expectedInputVersion
                || currentVersions.inputVersion() != expectedInputVersion
                || currentVersions.planVersion() != planVersion) {
            throw new IllegalStateException("准备任务输入版本已经过期");
        }
        if (!CreateQuickInterviewCommand.AI_AGENT_DEVELOPMENT.equals(
                snapshot.direction()
        )) {
            throw new IllegalStateException("快速面试岗位方向不合法");
        }
        ensureNoPartialPreparation(ownerId, interviewId);

        QuickInterviewBlueprint blueprint = blueprintPlanner.plan(interviewId);
        List<QuestionWrite> questions = prepareQuestions(
                interviewId,
                snapshot.direction(),
                blueprint,
                planVersion
        );
        validatePreparedPool(blueprint, questions);

        topicRepository.insertAll(
                ownerId,
                interviewId,
                planVersion,
                blueprint.topics().stream()
                        .map(topic -> topic.sequenceNo() == 1
                                ? topic.activate()
                                : topic)
                        .toList()
        );
        questionRepository.insertAll(ownerId, interviewId, questions);

        long expectedSessionVersion = aggregate.session().version();
        InterviewAggregate ready = STATE_MACHINE.transition(
                aggregate,
                InterviewEvent.PREPARATION_COMPLETED
        ).aggregate();
        if (!sessionRepository.updateIfVersionMatches(
                ownerId,
                ready,
                expectedSessionVersion
        )) {
            throw new IllegalStateException(
                    "面试状态已变化，准备结果不能发布"
            );
        }
        return new InterviewPreparationResult(
                interviewId,
                ready.session().stage(),
                questions.size(),
                ready.session().version()
        );
    }

    private List<QuestionWrite> prepareQuestions(
            UUID interviewId,
            String direction,
            QuickInterviewBlueprint blueprint,
            long planVersion
    ) {
        List<QuestionWrite> writes = new ArrayList<>();
        Set<String> acceptedFingerprints = new HashSet<>();
        for (PlannedTarget target : blueprint.targets()) {
            PreparedQuestion prepared = prepareQuestion(
                    direction,
                    target,
                    acceptedFingerprints
            );
            InterviewQuestion question = InterviewQuestion.reserved(
                    deterministicUuid(
                            "quick-question:" + interviewId + ":"
                                    + target.policy().code()
                    ),
                    target.topic().topicId(),
                    InterviewQuestion.QuestionType.MAIN,
                    Math.multiplyExact(target.topic().sequenceNo(), 10),
                    prepared.content()
            );
            if (target.topic().sequenceNo() == 1) {
                question = question.present();
            }
            writes.add(new QuestionWrite(
                    question,
                    null,
                    serializeSources(prepared.sources()),
                    prepared.fingerprint(),
                    planVersion
            ));
        }
        return List.copyOf(writes);
    }

    private PreparedQuestion prepareQuestion(
            String direction,
            PlannedTarget target,
            Set<String> acceptedFingerprints
    ) {
        List<KnowledgeQuestionCandidate> candidates = retrieveCandidates(
                direction,
                target.topic().objective()
        );
        if (!candidates.isEmpty()) {
            GeneratedQuestion generated = generateQuestion(
                    direction,
                    target.topic().objective(),
                    candidates
            );
            if (generated != null) {
                PreparedQuestion prepared = qualified(
                        generated.content(),
                        generated.sourceReferences(),
                        acceptedFingerprints
                );
                if (prepared != null) {
                    return prepared;
                }
            }
        }

        PreparedQuestion backup = qualified(
                target.policy().backupQuestion(),
                target.policy().backupSources(),
                acceptedFingerprints
        );
        if (backup == null) {
            throw new IllegalStateException(
                    "管理员备用题不合格或与本场题目重复"
            );
        }
        return backup;
    }

    private GeneratedQuestion generateQuestion(
            String direction,
            String objective,
            List<KnowledgeQuestionCandidate> candidates
    ) {
        try {
            var generated = modelPort.generateQuestion(
                    new QuestionGenerationRequest(
                            direction,
                            objective,
                            candidates
                    )
            );
            return generated == null ? null : generated.orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private List<KnowledgeQuestionCandidate> retrieveCandidates(
            String direction,
            String objective
    ) {
        try {
            List<KnowledgeQuestionCandidate> candidates = knowledgePort.search(
                    direction,
                    objective,
                    KNOWLEDGE_CANDIDATE_LIMIT
            );
            if (candidates == null) {
                return List.of();
            }
            return candidates.stream()
                    .filter(Objects::nonNull)
                    .limit(KNOWLEDGE_CANDIDATE_LIMIT)
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private static PreparedQuestion qualified(
            String content,
            List<SourceReference> sources,
            Set<String> acceptedFingerprints
    ) {
        if (!isQualifiedContent(content)
                || sources == null
                || sources.isEmpty()
                || sources.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        String normalized = normalize(content);
        String fingerprint = sha256(normalized);
        if (!acceptedFingerprints.add(fingerprint)) {
            return null;
        }
        return new PreparedQuestion(
                content.strip(),
                List.copyOf(sources),
                fingerprint
        );
    }

    private static boolean isQualifiedContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String stripped = content.strip();
        return stripped.length() >= MIN_QUESTION_LENGTH
                && stripped.length() <= MAX_QUESTION_LENGTH
                && stripped.chars().noneMatch(Character::isISOControl);
    }

    private static void validatePreparedPool(
            QuickInterviewBlueprint blueprint,
            List<QuestionWrite> questions
    ) {
        int required = QuickInterviewPolicyPack.REQUIRED_TARGET_COUNT;
        if (blueprint.targets().size() != required
                || questions.size() != required) {
            throw new IllegalStateException(
                    "快速面试未准备足够的合格主问题"
            );
        }
        long presented = questions.stream()
                .filter(write -> write.question().status()
                        == QuestionStatus.PRESENTED)
                .count();
        long reserved = questions.stream()
                .filter(write -> write.question().status()
                        == QuestionStatus.RESERVED)
                .count();
        long distinctTopics = questions.stream()
                .map(write -> write.question().topicId())
                .distinct()
                .count();
        long distinctFingerprints = questions.stream()
                .map(QuestionWrite::contentFingerprint)
                .distinct()
                .count();
        if (presented != 1
                || reserved != required - 1L
                || distinctTopics != required
                || distinctFingerprints != required) {
            throw new IllegalStateException(
                    "快速面试问题池不满足首题、预留题或去重约束"
            );
        }
    }

    private void ensureNoPartialPreparation(
            ActorId ownerId,
            UUID interviewId
    ) {
        if (!topicRepository.findTopics(ownerId, interviewId).isEmpty()
                || !questionRepository.findQuestions(
                        ownerId,
                        interviewId
                ).isEmpty()) {
            throw new IllegalStateException(
                    "PREPARING会话存在未发布的准备结果，需先恢复任务事务"
            );
        }
    }

    private InterviewPreparationResult existingReadyResult(
            ActorId ownerId,
            InterviewAggregate aggregate
    ) {
        UUID interviewId = aggregate.session().interviewId();
        int topicCount = topicRepository.findTopics(
                ownerId,
                interviewId
        ).size();
        List<InterviewQuestion> questions = questionRepository.findQuestions(
                ownerId,
                interviewId
        );
        if (topicCount != QuickInterviewPolicyPack.REQUIRED_TARGET_COUNT
                || questions.size()
                != QuickInterviewPolicyPack.REQUIRED_TARGET_COUNT
                || questions.stream().filter(question -> question.status()
                == QuestionStatus.PRESENTED).count() != 1
                || questions.stream().anyMatch(question ->
                !isQualifiedContent(question.content()))) {
            throw new IllegalStateException(
                    "READY会话缺少完整且合格的准备结果"
            );
        }
        return new InterviewPreparationResult(
                interviewId,
                SessionStage.READY,
                questions.size(),
                aggregate.session().version()
        );
    }

    private InterviewAggregate findInterview(
            ActorId ownerId,
            UUID interviewId
    ) {
        return sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new InterviewFlowNotFoundException(
                        interviewId
                ));
    }

    private InputSnapshot findSnapshot(
            ActorId ownerId,
            UUID interviewId
    ) {
        return inputSnapshotRepository.findBySession(ownerId, interviewId)
                .orElseThrow(() -> new IllegalStateException(
                        "快速面试缺少输入快照"
                ));
    }

    private static void requireQuickInterview(InterviewAggregate aggregate) {
        if (aggregate.session().mode() != InterviewMode.QUICK) {
            throw new IllegalStateException("该准备入口只支持快速面试");
        }
    }

    private String serializeSources(List<SourceReference> sources) {
        try {
            return jsonMapper.writeValueAsString(sources);
        } catch (JacksonException exception) {
            throw new IllegalStateException("题目来源序列化失败", exception);
        }
    }

    private static String normalize(String content) {
        return content.strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT);
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

    private record PreparedQuestion(
            String content,
            List<SourceReference> sources,
            String fingerprint
    ) {
    }

    /**
     * 对调用方只暴露准备终态和数量，不泄露隐藏考查目标或题目正文。
     */
    public record InterviewPreparationResult(
            UUID interviewId,
            SessionStage stage,
            int preparedQuestionCount,
            long sessionVersion
    ) {

        public InterviewPreparationResult {
            Objects.requireNonNull(interviewId, "interviewId不能为空");
            Objects.requireNonNull(stage, "stage不能为空");
            if (preparedQuestionCount < 0 || sessionVersion < 0) {
                throw new IllegalArgumentException("准备结果计数或版本不合法");
            }
        }
    }
}
