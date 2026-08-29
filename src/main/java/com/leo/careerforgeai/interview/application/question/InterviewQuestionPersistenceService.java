package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionInput;
import com.leo.careerforgeai.interview.application.model.validation.InterviewRoleContractErrorType;
import com.leo.careerforgeai.interview.application.model.validation.InterviewRoleContractException;
import com.leo.careerforgeai.interview.application.port.InterviewNodeExecutionRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewNodeExecution;
import com.leo.careerforgeai.interview.domain.InterviewNodeExecutionStatus;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRole;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 幂等认领任意回合问题生成并原子保存问题、模型收据和等待回答状态
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Service
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        InterviewRoundRepository.class,
        InterviewNodeExecutionRepository.class
})
public class InterviewQuestionPersistenceService {

    public static final String GENERATE_QUESTION_NODE = "generate_question";

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewRoundRepository roundRepository;
    private final InterviewNodeExecutionRepository executionRepository;
    private final InterviewQuestionFactory questionFactory;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public InterviewQuestionPersistenceService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewRoundRepository roundRepository,
            InterviewNodeExecutionRepository executionRepository,
            InterviewQuestionFactory questionFactory,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository不能为空");
        this.questionFactory = Objects.requireNonNull(questionFactory, "questionFactory不能为空");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public Optional<InterviewQuestion> startQuestionGeneration(UUID interviewId, int roundNo) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        if (roundNo < 1) throw new IllegalArgumentException("roundNo必须从1开始");

        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);
        Optional<InterviewQuestion> existing = findQuestion(ownerId, interviewId, roundNo);

        if (existing.isPresent()) {
            if (session.status() == InterviewStatus.CREATED) {
                throw new IllegalStateException("CREATED面试不能已经存在问题");
            }
            if (session.status() == InterviewStatus.GENERATING_QUESTION) {
                updateSession(ownerId, session, session.waitForAnswer(clock.instant()));
            }
            return existing;
        }

        List<InterviewQuestion> questions = roundRepository.findQuestions(ownerId, interviewId);
        if (questions.size() != roundNo - 1) {
            throw new IllegalStateException("问题历史与待生成回合号不连续");
        }

        if (roundNo == 1 && session.status() == InterviewStatus.CREATED) {
            updateSession(ownerId, session, session.startQuestionGeneration(clock.instant()));
            return Optional.empty();
        }
        if (session.status() != InterviewStatus.GENERATING_QUESTION) {
            throw new IllegalStateException("只有GENERATING_QUESTION状态可以生成后续问题");
        }
        if (roundNo > 1) requirePreviousRoundReviewed(ownerId, interviewId, roundNo, questions);
        return Optional.empty();
    }

    @Transactional
    public InterviewQuestion persistQuestion(
            UUID interviewId,
            InterviewQuestionInput input,
            InterviewRouteDecision routeDecision,
            InterviewRoleModelGateway.Result<InterviewQuestionDraft> result
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(input, "input不能为空");
        Objects.requireNonNull(result, "result不能为空");
        requireInputScope(interviewId, input, routeDecision);

        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);
        Optional<InterviewQuestion> existing = findQuestion(ownerId, interviewId, input.roundNo());
        if (existing.isPresent()) {
            requireExistingRoute(existing.get(), routeDecision);
            if (session.status() == InterviewStatus.GENERATING_QUESTION) {
                updateSession(ownerId, session, session.waitForAnswer(clock.instant()));
            }
            return existing.get();
        }
        if (session.status() != InterviewStatus.GENERATING_QUESTION) {
            throw new IllegalStateException("只有GENERATING_QUESTION状态可以保存问题");
        }

        List<InterviewQuestion> questions = roundRepository.findQuestions(ownerId, interviewId);
        if (questions.size() != input.roundNo() - 1) {
            throw new IllegalStateException("问题历史与待保存回合号不连续");
        }
        if (input.roundNo() > 1) {
            requirePreviousRoundReviewed(ownerId, interviewId, input.roundNo(), questions);
        }

        boolean followUp = routeDecision == InterviewRouteDecision.FOLLOW_UP;
        UUID parentQuestionId = followUp
                ? questions.get(questions.size() - 1).questionId()
                : null;
        Instant now = clock.instant();
        String inputHash = questionInputHash(input, routeDecision);
        InterviewNodeExecution execution = acquireExecution(
                ownerId,
                session,
                input.roundNo(),
                inputHash,
                now
        );
        UUID roundId = UUID.randomUUID();
        InterviewRound round = InterviewRound.questionReady(
                roundId,
                interviewId,
                ownerId,
                input.roundNo(),
                now
        );
        InterviewQuestion candidate = questionFactory.createQuestion(
                UUID.randomUUID(),
                interviewId,
                roundId,
                ownerId,
                parentQuestionId,
                followUp,
                result,
                now
        );

        rejectDuplicateQuestion(questions, candidate);
        InterviewQuestion stored = roundRepository.claimQuestionReadyRound(round, candidate);
        requireStoredQuestionScope(ownerId, interviewId, input.roundNo(), parentQuestionId, followUp, stored);
        completeExecution(execution, stored, result, now);
        updateSession(ownerId, session, session.waitForAnswer(now));
        return stored;
    }

    @Transactional
    public void failQuestionGeneration(UUID interviewId, InterviewFailureCode failureCode) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(failureCode, "failureCode不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);
        if (session.isTerminal()) return;
        if (session.status() != InterviewStatus.GENERATING_QUESTION) {
            throw new IllegalStateException("只有GENERATING_QUESTION状态可以收敛问题生成失败");
        }
        updateSession(ownerId, session, session.fail(failureCode, clock.instant()));
    }

    public Optional<InterviewQuestion> startFirstQuestionGeneration(UUID interviewId) {
        return startQuestionGeneration(interviewId, 1);
    }

    @Transactional
    public InterviewQuestion persistFirstQuestion(
            UUID interviewId,
            InterviewRoleModelGateway.Result<InterviewQuestionDraft> result
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(result, "result不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);
        Optional<InterviewQuestion> existing = findQuestion(ownerId, interviewId, 1);
        if (existing.isPresent()) {
            if (session.status() == InterviewStatus.GENERATING_QUESTION) {
                updateSession(ownerId, session, session.waitForAnswer(clock.instant()));
            }
            return existing.get();
        }
        if (session.status() != InterviewStatus.GENERATING_QUESTION) {
            throw new IllegalStateException("只有GENERATING_QUESTION状态可以保存首题");
        }

        Instant now = clock.instant();
        InterviewNodeExecution execution = acquireExecution(
                ownerId,
                session,
                1,
                legacyFirstQuestionInputHash(session),
                now
        );
        UUID roundId = UUID.randomUUID();
        InterviewRound round = InterviewRound.questionReady(roundId, interviewId, ownerId, 1, now);
        InterviewQuestion candidate = questionFactory.createFirstQuestion(
                UUID.randomUUID(),
                interviewId,
                roundId,
                ownerId,
                result,
                now
        );
        InterviewQuestion stored = roundRepository.claimQuestionReadyRound(round, candidate);
        requireStoredQuestionScope(ownerId, interviewId, 1, null, false, stored);
        completeExecution(execution, stored, result, now);
        updateSession(ownerId, session, session.waitForAnswer(now));
        return stored;
    }

    public void failFirstQuestionGeneration(UUID interviewId, InterviewFailureCode failureCode) {
        failQuestionGeneration(interviewId, failureCode);
    }

    private void requireInputScope(
            UUID interviewId,
            InterviewQuestionInput input,
            InterviewRouteDecision routeDecision
    ) {
        if (!input.interviewId().equals(interviewId)) {
            throw new IllegalArgumentException("InterviewQuestionInput不属于当前面试");
        }
        if (input.roundNo() == 1 && routeDecision != null) {
            throw new IllegalArgumentException("首题不能包含后续路由");
        }
        if (input.roundNo() > 1
                && routeDecision != InterviewRouteDecision.FOLLOW_UP
                && routeDecision != InterviewRouteDecision.NEXT_QUESTION) {
            throw new IllegalArgumentException("后续问题必须来自FOLLOW_UP或NEXT_QUESTION");
        }
    }

    private void requireExistingRoute(
            InterviewQuestion question,
            InterviewRouteDecision routeDecision
    ) {
        boolean expectedFollowUp = routeDecision == InterviewRouteDecision.FOLLOW_UP;
        if (question.followUp() != expectedFollowUp) {
            throw new IllegalStateException("已持久化问题与当前路由不一致");
        }
    }

    private void requirePreviousRoundReviewed(
            ActorId ownerId,
            UUID interviewId,
            int roundNo,
            List<InterviewQuestion> questions
    ) {
        InterviewRound previousRound = roundRepository
                .findRoundByNumber(ownerId, interviewId, roundNo - 1)
                .orElseThrow(() -> new IllegalStateException("MySQL缺少上一面试回合"));
        InterviewQuestion previousQuestion = questions.get(questions.size() - 1);
        if (previousRound.status() != InterviewRoundStatus.REVIEWED
                || !previousRound.roundId().equals(previousQuestion.roundId())
                || !previousRound.interviewId().equals(interviewId)
                || !previousRound.ownerId().equals(ownerId)) {
            throw new IllegalStateException("上一问题尚未完成评审或作用域不一致");
        }
    }

    private void rejectDuplicateQuestion(
            List<InterviewQuestion> previousQuestions,
            InterviewQuestion candidate
    ) {
        String candidateText = normalizeQuestionText(candidate.questionText());
        boolean duplicate = previousQuestions.stream()
                .map(InterviewQuestion::questionText)
                .map(this::normalizeQuestionText)
                .anyMatch(candidateText::equals);
        if (duplicate) {
            throw new InterviewRoleContractException(
                    InterviewRole.INTERVIEWER,
                    InterviewRoleContractErrorType.OUTPUT_INVALID,
                    "模型生成了重复问题"
            );
        }
    }

    private String normalizeQuestionText(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private InterviewNodeExecution acquireExecution(
            ActorId ownerId,
            MockInterviewSession session,
            int roundNo,
            String inputHash,
            Instant now
    ) {
        InterviewNodeExecution candidate = InterviewNodeExecution.start(
                UUID.randomUUID(),
                session.interviewId(),
                ownerId,
                roundNo,
                GENERATE_QUESTION_NODE,
                inputHash,
                now
        );
        InterviewNodeExecution stored = executionRepository.claim(candidate);
        if (stored.status() == InterviewNodeExecutionStatus.SUCCEEDED) {
            throw new IllegalStateException("问题节点已成功但缺少问题事实");
        }
        if (stored.status() == InterviewNodeExecutionStatus.RUNNING) {
            if (stored.executionId().equals(candidate.executionId())) return stored;
            throw new IllegalStateException("问题生成已经由另一个执行者处理");
        }

        InterviewNodeExecution retried = stored.retry(now);
        if (!executionRepository.updateIfVersionMatches(ownerId, retried, stored.version())) {
            throw new IllegalStateException("问题生成重试执行权CAS认领失败");
        }
        return retried;
    }

    private void completeExecution(
            InterviewNodeExecution execution,
            InterviewQuestion question,
            InterviewRoleModelGateway.Result<InterviewQuestionDraft> result,
            Instant now
    ) {
        InterviewNodeExecution succeeded = execution.succeed(
                question.questionId().toString(),
                result.requestId(),
                result.modelCallCount(),
                result.usage(),
                result.durationMs(),
                now
        );
        if (!executionRepository.updateIfVersionMatches(
                execution.ownerId(),
                succeeded,
                execution.version()
        )) {
            throw new IllegalStateException("问题节点成功状态CAS更新失败");
        }
    }

    private String questionInputHash(
            InterviewQuestionInput input,
            InterviewRouteDecision routeDecision
    ) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("schemaVersion", 2);
        fingerprint.put("node", GENERATE_QUESTION_NODE);
        fingerprint.put("routeDecision", routeDecision == null ? "FIRST_QUESTION" : routeDecision.name());
        fingerprint.put("input", input);
        try {
            return sha256(jsonMapper.writeValueAsString(fingerprint));
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化问题节点输入Hash失败", exception);
        }
    }

    private String legacyFirstQuestionInputHash(MockInterviewSession session) {
        String canonical = String.join(
                "|",
                "schemaVersion=1",
                "node=" + GENERATE_QUESTION_NODE,
                "interviewId=" + session.interviewId(),
                "mode=" + session.mode(),
                "inputSnapshotHash=" + session.inputSnapshotHash(),
                "roundNo=1"
        );
        return sha256(canonical);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private void requireStoredQuestionScope(
            ActorId ownerId,
            UUID interviewId,
            int roundNo,
            UUID parentQuestionId,
            boolean followUp,
            InterviewQuestion question
    ) {
        if (!question.ownerId().equals(ownerId)
                || !question.interviewId().equals(interviewId)
                || question.followUp() != followUp
                || !Objects.equals(question.parentQuestionId(), parentQuestionId)) {
            throw new IllegalStateException("问题幂等认领结果作用域或路由不一致");
        }

        InterviewRound round = roundRepository
                .findRoundByNumber(ownerId, interviewId, roundNo)
                .orElseThrow(() -> new IllegalStateException("问题保存后缺少对应回合"));
        if (!round.roundId().equals(question.roundId())) {
            throw new IllegalStateException("问题与持久化回合不一致");
        }
    }

    private Optional<InterviewQuestion> findQuestion(
            ActorId ownerId,
            UUID interviewId,
            int roundNo
    ) {
        return roundRepository.findRoundByNumber(ownerId, interviewId, roundNo)
                .map(round -> roundRepository.findQuestionByRound(ownerId, interviewId, round.roundId())
                        .orElseThrow(() -> new IllegalStateException("回合存在但缺少问题事实")));
    }

    private MockInterviewSession requireSession(ActorId ownerId, UUID interviewId) {
        return sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
    }

    private void updateSession(
            ActorId ownerId,
            MockInterviewSession current,
            MockInterviewSession updated
    ) {
        if (!sessionRepository.updateIfVersionMatches(ownerId, updated, current.version())) {
            throw new MockInterviewVersionConflictException(current.interviewId(), current.version());
        }
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}