package com.leo.careerforgeai.interview.application.review;

import com.leo.careerforgeai.interview.application.port.InterviewNodeExecutionRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.review.EvidenceReview;
import com.leo.careerforgeai.interview.domain.execution.InterviewNodeExecution;
import com.leo.careerforgeai.interview.domain.execution.InterviewNodeExecutionStatus;
import com.leo.careerforgeai.interview.domain.review.TechnicalReview;
import com.leo.careerforgeai.model.domain.ModelUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 在短事务中原子保存评审事实并完成对应节点执行记录
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Service
@ConditionalOnBean({
        InterviewReviewRepository.class,
        InterviewNodeExecutionRepository.class
})
public class InterviewReviewPersistenceService {

    private final InterviewReviewRepository reviewRepository;
    private final InterviewNodeExecutionRepository executionRepository;
    private final Clock clock;

    public InterviewReviewPersistenceService(
            InterviewReviewRepository reviewRepository,
            InterviewNodeExecutionRepository executionRepository,
            Clock clock
    ) {
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository不能为空");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public TechnicalReview persistTechnical(
            TechnicalReview candidate,
            InterviewNodeExecution execution,
            InterviewRoleModelGateway.Result<?> result
    ) {
        requireRunning(execution, candidate.inputHash());
        TechnicalReview stored = reviewRepository.claimTechnicalReview(candidate);
        requireTechnicalScope(candidate, stored);
        complete(execution, stored.technicalReviewId().toString(), result);
        return stored;
    }

    @Transactional
    public EvidenceReview persistEvidence(
            EvidenceReview candidate,
            InterviewNodeExecution execution,
            InterviewRoleModelGateway.Result<?> result
    ) {
        requireRunning(execution, candidate.inputHash());
        EvidenceReview stored = reviewRepository.claimEvidenceReview(candidate);
        requireEvidenceScope(candidate, stored);
        complete(execution, stored.evidenceReviewId().toString(), result);
        return stored;
    }

    @Transactional
    public void fail(InterviewNodeExecution execution, String failureCode) {
        Objects.requireNonNull(execution, "execution不能为空");
        if (execution.status() != InterviewNodeExecutionStatus.RUNNING) return;

        InterviewNodeExecution failed = execution.failWithoutModel(
                Objects.requireNonNull(failureCode, "failureCode不能为空"),
                clock.instant()
        );
        if (!executionRepository.updateIfVersionMatches(
                execution.ownerId(),
                failed,
                execution.version()
        )) {
            throw new IllegalStateException("评审节点失败状态CAS更新失败");
        }
    }

    private void complete(
            InterviewNodeExecution execution,
            String outputReferenceId,
            InterviewRoleModelGateway.Result<?> result
    ) {
        InterviewNodeExecution succeeded = result == null
                ? execution.succeed(
                        outputReferenceId,
                        null,
                        0,
                        new ModelUsage(0, 0, 0),
                        0,
                        clock.instant()
                )
                : execution.succeed(
                        outputReferenceId,
                        result.requestId(),
                        result.modelCallCount(),
                        result.usage(),
                        result.durationMs(),
                        clock.instant()
                );

        if (!executionRepository.updateIfVersionMatches(
                execution.ownerId(),
                succeeded,
                execution.version()
        )) {
            throw new IllegalStateException("评审节点成功状态CAS更新失败");
        }
    }

    private void requireRunning(InterviewNodeExecution execution, String inputHash) {
        Objects.requireNonNull(execution, "execution不能为空");
        if (execution.status() != InterviewNodeExecutionStatus.RUNNING
                || !execution.inputHash().equals(inputHash)) {
            throw new IllegalStateException("评审事实与节点执行身份不一致");
        }
    }

    private void requireTechnicalScope(TechnicalReview expected, TechnicalReview stored) {
        if (!stored.interviewId().equals(expected.interviewId())
                || !stored.roundId().equals(expected.roundId())
                || !stored.questionId().equals(expected.questionId())
                || !stored.answerId().equals(expected.answerId())
                || !stored.ownerId().equals(expected.ownerId())
                || !stored.inputHash().equals(expected.inputHash())) {
            throw new IllegalStateException("技术评审幂等认领结果作用域不一致");
        }
    }

    private void requireEvidenceScope(EvidenceReview expected, EvidenceReview stored) {
        if (!stored.interviewId().equals(expected.interviewId())
                || !stored.roundId().equals(expected.roundId())
                || !stored.questionId().equals(expected.questionId())
                || !stored.answerId().equals(expected.answerId())
                || !stored.ownerId().equals(expected.ownerId())
                || !stored.inputHash().equals(expected.inputHash())) {
            throw new IllegalStateException("证据评审幂等认领结果作用域不一致");
        }
    }
}