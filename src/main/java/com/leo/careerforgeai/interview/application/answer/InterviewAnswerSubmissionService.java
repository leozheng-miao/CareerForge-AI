package com.leo.careerforgeai.interview.application.answer;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 幂等保存用户原始答案并原子推进回合和面试状态
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
@Service
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        InterviewRoundRepository.class
})
public class InterviewAnswerSubmissionService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewRoundRepository roundRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public InterviewAnswerSubmissionService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewRoundRepository roundRepository,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public InterviewAnswer submit(
            UUID interviewId,
            int roundNo,
            UUID questionId,
            UUID requestId,
            long expectedInterviewVersion,
            String answerText
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");
        if (roundNo < 1) throw new IllegalArgumentException("roundNo必须从1开始");
        if (expectedInterviewVersion < 0) {
            throw new IllegalArgumentException("expectedInterviewVersion不能小于0");
        }
        requireAnswerText(answerText);

        ActorId ownerId = currentActor();
        String contentHash = sha256(answerText);
        String requestFingerprint = requestFingerprint(
                interviewId,
                roundNo,
                questionId,
                requestId,
                expectedInterviewVersion,
                contentHash
        );

        Optional<InterviewAnswer> requestReplay =
                roundRepository.findAnswerByRequest(ownerId, requestId);
        if (requestReplay.isPresent()) {
            return requireRequestReplay(
                    requestReplay.get(),
                    interviewId,
                    questionId,
                    requestFingerprint
            );
        }

        MockInterviewSession session = sessionRepository
                .findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
        InterviewRound round = roundRepository
                .findRoundByNumber(ownerId, interviewId, roundNo)
                .orElseThrow(() -> new IllegalStateException("当前面试回合不存在"));
        InterviewQuestion question = roundRepository
                .findQuestionByRound(ownerId, interviewId, round.roundId())
                .orElseThrow(() -> new IllegalStateException("当前面试问题不存在"));

        requireQuestionScope(ownerId, interviewId, questionId, round, question);

        Optional<InterviewAnswer> questionReplay =
                roundRepository.findAnswerByQuestion(ownerId, interviewId, questionId);
        if (questionReplay.isPresent()) {
            return requireQuestionReplay(questionReplay.get(), contentHash);
        }

        if (session.status() != InterviewStatus.WAITING_FOR_ANSWER) {
            throw new IllegalStateException("只有WAITING_FOR_ANSWER状态可以提交答案");
        }
        if (session.version() != expectedInterviewVersion) {
            throw new MockInterviewVersionConflictException(
                    interviewId,
                    expectedInterviewVersion
            );
        }
        if (round.status() != InterviewRoundStatus.QUESTION_READY) {
            throw new IllegalStateException("只有QUESTION_READY回合可以提交答案");
        }

        Instant now = clock.instant();
        InterviewAnswer candidate = new InterviewAnswer(
                UUID.randomUUID(),
                interviewId,
                round.roundId(),
                questionId,
                ownerId,
                requestId,
                requestFingerprint,
                expectedInterviewVersion,
                answerText,
                contentHash,
                now
        );
        InterviewAnswer stored = roundRepository.claimAnswer(candidate);
        requireStoredAnswer(candidate, stored);

        MockInterviewSession reviewing = session.startReview(now);
        if (!sessionRepository.updateIfVersionMatches(
                ownerId,
                reviewing,
                session.version()
        )) {
            throw new MockInterviewVersionConflictException(
                    interviewId,
                    expectedInterviewVersion
            );
        }

        InterviewRound answered = round.answer(now);
        if (!roundRepository.updateRoundIfVersionMatches(
                ownerId,
                answered,
                round.version()
        )) {
            throw new IllegalStateException("回合CAS更新失败");
        }
        return stored;
    }

    private InterviewAnswer requireRequestReplay(
            InterviewAnswer stored,
            UUID interviewId,
            UUID questionId,
            String requestFingerprint
    ) {
        if (!stored.interviewId().equals(interviewId)
                || !stored.questionId().equals(questionId)
                || !stored.requestFingerprint().equals(requestFingerprint)) {
            throw new IllegalStateException("requestId已被不同答案请求使用");
        }
        return stored;
    }

    private InterviewAnswer requireQuestionReplay(
            InterviewAnswer stored,
            String contentHash
    ) {
        if (!stored.contentHash().equals(contentHash)) {
            throw new IllegalStateException("当前问题已经提交了不同答案");
        }
        return stored;
    }

    private void requireQuestionScope(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId,
            InterviewRound round,
            InterviewQuestion question
    ) {
        if (!round.ownerId().equals(ownerId)
                || !round.interviewId().equals(interviewId)
                || !question.ownerId().equals(ownerId)
                || !question.interviewId().equals(interviewId)
                || !question.roundId().equals(round.roundId())
                || !question.questionId().equals(questionId)) {
            throw new IllegalStateException("问题、回合或owner作用域不一致");
        }
    }

    private void requireStoredAnswer(
            InterviewAnswer expected,
            InterviewAnswer stored
    ) {
        if (!stored.ownerId().equals(expected.ownerId())
                || !stored.interviewId().equals(expected.interviewId())
                || !stored.roundId().equals(expected.roundId())
                || !stored.questionId().equals(expected.questionId())
                || !stored.contentHash().equals(expected.contentHash())) {
            throw new IllegalStateException("答案幂等认领结果与当前请求不一致");
        }
    }

    private String requestFingerprint(
            UUID interviewId,
            int roundNo,
            UUID questionId,
            UUID requestId,
            long expectedInterviewVersion,
            String contentHash
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("schemaVersion", 1);
        request.put("interviewId", interviewId);
        request.put("roundNo", roundNo);
        request.put("questionId", questionId);
        request.put("requestId", requestId);
        request.put("expectedInterviewVersion", expectedInterviewVersion);
        request.put("contentHash", contentHash);

        try {
            return sha256(jsonMapper.writeValueAsString(request));
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化答案请求指纹失败", exception);
        }
    }

    private void requireAnswerText(String answerText) {
        if (answerText == null || answerText.isBlank()
                || answerText.length() > 12_000) {
            throw new IllegalArgumentException(
                    "answerText不能为空且长度不能超过12000"
            );
        }
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

    private ActorId currentActor() {
        return Objects.requireNonNull(
                currentActorProvider.currentActor(),
                "currentActor不能为空"
        );
    }
}