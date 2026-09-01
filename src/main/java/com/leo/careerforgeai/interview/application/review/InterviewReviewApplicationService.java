package com.leo.careerforgeai.interview.application.review;

import com.leo.careerforgeai.interview.application.model.review.EvidenceReviewDraft;
import com.leo.careerforgeai.interview.application.model.review.TechnicalReviewDraft;
import com.leo.careerforgeai.interview.application.model.review.EvidenceReviewRoleContract;
import com.leo.careerforgeai.interview.application.model.review.TechnicalReviewRoleContract;
import com.leo.careerforgeai.interview.application.port.InterviewNodeExecutionRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.review.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.review.EvidenceReview;
import com.leo.careerforgeai.interview.domain.review.EvidenceReviewSource;
import com.leo.careerforgeai.interview.domain.session.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.execution.InterviewNodeExecution;
import com.leo.careerforgeai.interview.domain.execution.InterviewNodeExecutionStatus;
import com.leo.careerforgeai.interview.domain.review.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.review.TechnicalReview;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 认领并执行技术评审和证据一致性评审节点
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Service
@ConditionalOnBean({
        InterviewReviewRepository.class,
        InterviewNodeExecutionRepository.class,
        InterviewRoleModelGateway.class
})
public class InterviewReviewApplicationService {

    public static final String TECHNICAL_NODE = "technical_review";
    public static final String EVIDENCE_NODE = "evidence_review";

    private final InterviewReviewPreparationService preparationService;
    private final InterviewReviewPersistenceService persistenceService;
    private final InterviewReviewRepository reviewRepository;
    private final InterviewNodeExecutionRepository executionRepository;
    private final InterviewRoleModelGateway modelGateway;
    private final TechnicalReviewRoleContract technicalContract;
    private final EvidenceReviewRoleContract evidenceContract;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public InterviewReviewApplicationService(
            InterviewReviewPreparationService preparationService,
            InterviewReviewPersistenceService persistenceService,
            InterviewReviewRepository reviewRepository,
            InterviewNodeExecutionRepository executionRepository,
            InterviewRoleModelGateway modelGateway,
            TechnicalReviewRoleContract technicalContract,
            EvidenceReviewRoleContract evidenceContract,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.preparationService = Objects.requireNonNull(preparationService, "preparationService不能为空");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService不能为空");
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository不能为空");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository不能为空");
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway不能为空");
        this.technicalContract = Objects.requireNonNull(technicalContract, "technicalContract不能为空");
        this.evidenceContract = Objects.requireNonNull(evidenceContract, "evidenceContract不能为空");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public InterviewReviewPlan preparePlan(
            UUID interviewId,
            int roundNo,
            UUID questionId,
            UUID answerId
    ) {
        return preparationService.prepare(interviewId, roundNo, questionId, answerId).plan();
    }

    public TechnicalReview reviewTechnical(
            UUID interviewId,
            int roundNo,
            UUID questionId,
            UUID answerId,
            Duration timeout
    ) {
        requireTimeout(timeout);
        var prepared = preparationService.prepare(interviewId, roundNo, questionId, answerId);
        String inputHash = hash(prepared.technicalInput());

        Optional<TechnicalReview> existing = reviewRepository.findTechnicalReviewByAnswer(
                prepared.ownerId(),
                interviewId,
                answerId
        );
        if (existing.isPresent())   return requireTechnicalInput(existing.get(), prepared, inputHash);


        InterviewNodeExecution execution = acquire(
                prepared.ownerId(),
                interviewId,
                roundNo,
                TECHNICAL_NODE,
                inputHash
        );
        if (execution.status() == InterviewNodeExecutionStatus.SUCCEEDED) {
            return loadTechnicalSuccess(execution, prepared, inputHash);
        }

        try {
            InterviewRoleModelGateway.Result<TechnicalReviewDraft> result =
                    modelGateway.generate(technicalContract, prepared.technicalInput(), timeout);
            TechnicalReviewDraft draft = result.output();
            TechnicalReview candidate = new TechnicalReview(
                    UUID.randomUUID(),
                    interviewId,
                    prepared.roundId(),
                    questionId,
                    answerId,
                    prepared.ownerId(),
                    draft.dimensionScores(),
                    draft.coveredPoints(),
                    draft.errorsOrOmissions(),
                    draft.verificationBasis(),
                    draft.suggestedFollowUp(),
                    result.requestId(),
                    result.promptVersion(),
                    inputHash,
                    hash(draft),
                    clock.instant()
            );
            return persistenceService.persistTechnical(candidate, execution, result);
        } catch (RuntimeException exception) {
            fail(execution, exception);
            throw exception;
        }
    }

    public EvidenceReview reviewEvidence(
            UUID interviewId,
            int roundNo,
            UUID questionId,
            UUID answerId,
            Duration timeout
    ) {
        requireTimeout(timeout);
        var prepared = preparationService.prepare(interviewId, roundNo, questionId, answerId);
        String inputHash = hash(prepared.evidenceInput());

        Optional<EvidenceReview> existing = reviewRepository.findEvidenceReviewByAnswer(
                prepared.ownerId(),
                interviewId,
                answerId
        );
        if (existing.isPresent())  return requireEvidenceInput(existing.get(), prepared, inputHash);

        InterviewNodeExecution execution = acquire(
                prepared.ownerId(),
                interviewId,
                roundNo,
                EVIDENCE_NODE,
                inputHash
        );
        if (execution.status() == InterviewNodeExecutionStatus.SUCCEEDED) {
            return loadEvidenceSuccess(execution, prepared, inputHash);
        }

        try {
            if (prepared.plan() == InterviewReviewPlan.TECHNICAL_ONLY) {
                EvidenceReviewDraft draft = new EvidenceReviewDraft(
                        EvidenceConsistencyVerdict.NOT_APPLICABLE,
                        java.util.List.of(),
                        "当前问题不属于项目或经历深挖题，Java确定性跳过证据模型评审。"
                );
                EvidenceReview candidate = evidenceCandidate(
                        prepared,
                        questionId,
                        answerId,
                        inputHash,
                        draft,
                        EvidenceReviewSource.JAVA,
                        null
                );
                return persistenceService.persistEvidence(candidate, execution, null);
            }

            InterviewRoleModelGateway.Result<EvidenceReviewDraft> result =
                    modelGateway.generate(evidenceContract, prepared.evidenceInput(), timeout);
            EvidenceReview candidate = evidenceCandidate(
                    prepared,
                    questionId,
                    answerId,
                    inputHash,
                    result.output(),
                    EvidenceReviewSource.MODEL,
                    result
            );
            return persistenceService.persistEvidence(candidate, execution, result);
        } catch (RuntimeException exception) {
            fail(execution, exception);
            throw exception;
        }
    }

    private EvidenceReview evidenceCandidate(
            InterviewReviewPreparationService.PreparedReviews prepared,
            UUID questionId,
            UUID answerId,
            String inputHash,
            EvidenceReviewDraft draft,
            EvidenceReviewSource source,
            InterviewRoleModelGateway.Result<?> result
    ) {
        return new EvidenceReview(
                UUID.randomUUID(),
                prepared.technicalInput().interviewId(),
                prepared.roundId(),
                questionId,
                answerId,
                prepared.ownerId(),
                source,
                draft.verdict(),
                draft.evidenceReferenceIds(),
                draft.reason(),
                result == null ? null : result.requestId(),
                result == null ? null : result.promptVersion(),
                inputHash,
                hash(draft),
                clock.instant()
        );
    }

    private InterviewNodeExecution acquire(
            com.leo.careerforgeai.shared.actor.ActorId ownerId,
            UUID interviewId,
            int roundNo,
            String nodeName,
            String inputHash
    ) {
        InterviewNodeExecution candidate = InterviewNodeExecution.start(
                UUID.randomUUID(),
                interviewId,
                ownerId,
                roundNo,
                nodeName,
                inputHash,
                clock.instant()
        );
        InterviewNodeExecution stored = executionRepository.claim(candidate);

        if (stored.status() == InterviewNodeExecutionStatus.SUCCEEDED) return stored;
        if (stored.status() == InterviewNodeExecutionStatus.RUNNING) {
            if (stored.executionId().equals(candidate.executionId())) return stored;
            throw new IllegalStateException(nodeName + "已经由另一个执行者处理");
        }

        InterviewNodeExecution retried = stored.retry(clock.instant());
        if (!executionRepository.updateIfVersionMatches(ownerId, retried, stored.version())) {
            throw new IllegalStateException(nodeName + "重试执行权CAS认领失败");
        }
        return retried;
    }

    private TechnicalReview loadTechnicalSuccess(
            InterviewNodeExecution execution,
            InterviewReviewPreparationService.PreparedReviews prepared,
            String inputHash
    ) {
        UUID reviewId = UUID.fromString(execution.outputReferenceId());
        TechnicalReview review = reviewRepository.findTechnicalReviewById(
                execution.ownerId(),
                execution.interviewId(),
                reviewId
        ).orElseThrow(() -> new IllegalStateException("技术评审节点成功但缺少输出事实"));
        return requireTechnicalInput(review, prepared, inputHash);
    }

    private EvidenceReview loadEvidenceSuccess(
            InterviewNodeExecution execution,
            InterviewReviewPreparationService.PreparedReviews prepared,
            String inputHash
    ) {
        UUID reviewId = UUID.fromString(execution.outputReferenceId());
        EvidenceReview review = reviewRepository.findEvidenceReviewById(
                execution.ownerId(),
                execution.interviewId(),
                reviewId
        ).orElseThrow(() -> new IllegalStateException("证据评审节点成功但缺少输出事实"));
        return requireEvidenceInput(review, prepared, inputHash);
    }

    private TechnicalReview requireTechnicalInput(
            TechnicalReview review,
            InterviewReviewPreparationService.PreparedReviews prepared,
            String inputHash
    ) {
        var input = prepared.technicalInput();
        if (!review.ownerId().equals(prepared.ownerId())
                || !review.interviewId().equals(input.interviewId())
                || !review.roundId().equals(prepared.roundId())
                || !review.questionId().equals(input.questionId())
                || !review.answerId().equals(input.answerId())
                || !review.inputHash().equals(inputHash)) {
            throw new IllegalStateException("已有技术评审与当前冻结输入不一致");
        }
        return review;
    }

    private EvidenceReview requireEvidenceInput(
            EvidenceReview review,
            InterviewReviewPreparationService.PreparedReviews prepared,
            String inputHash
    ) {
        var input = prepared.evidenceInput();
        if (!review.ownerId().equals(prepared.ownerId())
                || !review.interviewId().equals(input.interviewId())
                || !review.roundId().equals(prepared.roundId())
                || !review.questionId().equals(input.questionId())
                || !review.answerId().equals(input.answerId())
                || !review.inputHash().equals(inputHash)) {
            throw new IllegalStateException("已有证据评审与当前冻结输入不一致");
        }
        return review;
    }

    private void fail(InterviewNodeExecution execution, RuntimeException exception) {
        try {
            persistenceService.fail(execution, failureCode(exception).name());
        } catch (RuntimeException convergenceFailure) {
            exception.addSuppressed(convergenceFailure);
        }
    }

    private InterviewFailureCode failureCode(RuntimeException exception) {
        if (exception instanceof ModelException modelException) {
            ModelErrorType errorType = modelException.getErrorType();
            if (errorType == ModelErrorType.INVALID_RESPONSE
                    || errorType == ModelErrorType.STRUCTURED_OUTPUT_INVALID) {
                return InterviewFailureCode.MODEL_OUTPUT_INVALID;
            }
            return InterviewFailureCode.MODEL_CALL_FAILED;
        }
        return InterviewFailureCode.INTERNAL_ERROR;
    }

    private String hash(Object value) {
        Objects.requireNonNull(value, "待Hash内容不能为空");
        try {
            return sha256(jsonMapper.writeValueAsString(value));
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化评审Hash内容失败", exception);
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

    private void requireTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout必须大于0");
        }
    }
}