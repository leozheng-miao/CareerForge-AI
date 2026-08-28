package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionDraft;
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
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
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

/**
 * @program: CareerForge-AI
 * @description: 使用短事务认领首题生成权并原子保存问题、模型收据和等待回答状态
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
    private final Clock clock;

    public InterviewQuestionPersistenceService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewRoundRepository roundRepository,
            InterviewNodeExecutionRepository executionRepository,
            InterviewQuestionFactory questionFactory,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository不能为空");
        this.questionFactory = Objects.requireNonNull(questionFactory, "questionFactory不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public Optional<InterviewQuestion> startFirstQuestionGeneration(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);
        Optional<InterviewQuestion> existing = findFirstQuestion(ownerId, interviewId);

        if (existing.isPresent()) {
            if (session.status() == InterviewStatus.CREATED) {
                throw new IllegalStateException("CREATED面试不能已经存在首题");
            }
            if (session.status() == InterviewStatus.GENERATING_QUESTION) {
                updateSession(ownerId, session, session.waitForAnswer(clock.instant()));
            }
            return existing;
        }

        if (session.status() == InterviewStatus.CREATED) {
            updateSession(ownerId, session, session.startQuestionGeneration(clock.instant()));
            return Optional.empty();
        }
        if (session.status() == InterviewStatus.GENERATING_QUESTION) return Optional.empty();
        throw new IllegalStateException("首题不存在时只有CREATED或GENERATING_QUESTION面试可以生成");
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
        Optional<InterviewQuestion> existing = findFirstQuestion(ownerId, interviewId);

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
                ownerId, session, firstQuestionInputHash(session), now
        );
        UUID roundId = UUID.randomUUID();
        InterviewRound round = InterviewRound.questionReady(roundId, interviewId, ownerId, 1, now);
        InterviewQuestion candidate = questionFactory.createFirstQuestion(
                UUID.randomUUID(), interviewId, roundId, ownerId, result, now
        );
        InterviewQuestion stored = roundRepository.claimQuestionReadyRound(round, candidate);
        requireStoredQuestionScope(ownerId, interviewId, stored);
        completeExecution(execution, stored, result, now);
        updateSession(ownerId, session, session.waitForAnswer(now));
        return stored;
    }

    @Transactional
    public void failFirstQuestionGeneration(UUID interviewId, InterviewFailureCode failureCode) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(failureCode, "failureCode不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);

        if (session.isTerminal()) return;
        if (session.status() != InterviewStatus.GENERATING_QUESTION) {
            throw new IllegalStateException("只有GENERATING_QUESTION状态可以收敛首题生成失败");
        }
        updateSession(ownerId, session, session.fail(failureCode, clock.instant()));
    }

    private InterviewNodeExecution acquireExecution(
            ActorId ownerId,
            MockInterviewSession session,
            String inputHash,
            Instant now
    ) {
        InterviewNodeExecution candidate = InterviewNodeExecution.start(
                UUID.randomUUID(),
                session.interviewId(),
                ownerId,
                1,
                GENERATE_QUESTION_NODE,
                inputHash,
                now
        );
        InterviewNodeExecution stored = executionRepository.claim(candidate);

        if (stored.status() == InterviewNodeExecutionStatus.SUCCEEDED) {
            throw new IllegalStateException("首题节点已成功但缺少问题事实");
        }
        if (stored.status() == InterviewNodeExecutionStatus.RUNNING) {
            if (stored.executionId().equals(candidate.executionId())) return stored;
            throw new IllegalStateException("首题生成已经由另一个执行者处理");
        }

        InterviewNodeExecution retried = stored.retry(now);
        if (!executionRepository.updateIfVersionMatches(ownerId, retried, stored.version())) {
            throw new IllegalStateException("首题生成重试执行权CAS认领失败");
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
                execution.ownerId(), succeeded, execution.version()
        )) {
            throw new IllegalStateException("首题节点成功状态CAS更新失败");
        }
    }

    private String firstQuestionInputHash(MockInterviewSession session) {
        String canonical = String.join(
                "|",
                "schemaVersion=1",
                "node=" + GENERATE_QUESTION_NODE,
                "interviewId=" + session.interviewId(),
                "mode=" + session.mode(),
                "inputSnapshotHash=" + session.inputSnapshotHash(),
                "roundNo=1"
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private void requireStoredQuestionScope(ActorId ownerId, UUID interviewId, InterviewQuestion question) {
        if (!question.ownerId().equals(ownerId) || !question.interviewId().equals(interviewId)) {
            throw new IllegalStateException("首题幂等认领结果作用域不一致");
        }
    }

    private Optional<InterviewQuestion> findFirstQuestion(ActorId ownerId, UUID interviewId) {
        return roundRepository.findRoundByNumber(ownerId, interviewId, 1)
                .map(round -> roundRepository.findQuestionByRound(ownerId, interviewId, round.roundId())
                        .orElseThrow(() -> new IllegalStateException("首回合存在但缺少问题事实")));
    }

    private MockInterviewSession requireSession(ActorId ownerId, UUID interviewId) {
        return sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
    }

    private void updateSession(ActorId ownerId, MockInterviewSession current, MockInterviewSession updated) {
        if (!sessionRepository.updateIfVersionMatches(ownerId, updated, current.version())) {
            throw new MockInterviewVersionConflictException(current.interviewId(), current.version());
        }
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}