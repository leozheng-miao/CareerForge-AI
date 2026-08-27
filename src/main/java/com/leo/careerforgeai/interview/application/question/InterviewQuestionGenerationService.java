package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.blueprint.InterviewBlueprintApplicationService;
import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionInput;
import com.leo.careerforgeai.interview.application.model.validation.InterviewQuestionRoleContract;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;

import java.util.Optional;

/**
 * @program: CareerForge-AI
 * @description: 从冻结面试输入生成经过Java角色契约校验的首题候选
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
@Service
@ConditionalOnBean({
        InterviewBlueprintApplicationService.class,
        InterviewRoleModelGateway.class,
        InterviewQuestionPersistenceService.class
})
public class InterviewQuestionGenerationService {

    private final InterviewBlueprintApplicationService blueprintService;
    private final InterviewQuestionRoleContract questionContract;
    private final InterviewRoleModelGateway modelGateway;
    private final InterviewQuestionPersistenceService persistenceService;

    public InterviewQuestionGenerationService(
            InterviewBlueprintApplicationService blueprintService,
            InterviewQuestionRoleContract questionContract,
            InterviewRoleModelGateway modelGateway,
            InterviewQuestionPersistenceService persistenceService
    ) {
        this.blueprintService = Objects.requireNonNull(blueprintService, "blueprintService不能为空");
        this.questionContract = Objects.requireNonNull(questionContract, "questionContract不能为空");
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway不能为空");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService不能为空");
    }

    public InterviewRoleModelGateway.Result<InterviewQuestionDraft> generateFirstQuestion(
            UUID interviewId,
            Duration timeout
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        requireTimeout(timeout);

        InterviewQuestionInput input = blueprintService.prepareFirstQuestion(interviewId);
        return modelGateway.generate(questionContract, input, timeout);
    }

    public InterviewQuestion generateAndPersistFirstQuestion(
            UUID interviewId,
            Duration timeout
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        requireTimeout(timeout);

        Optional<InterviewQuestion> existing =
                persistenceService.startFirstQuestionGeneration(interviewId);
        if (existing.isPresent()) return existing.get();

        InterviewRoleModelGateway.Result<InterviewQuestionDraft> result;
        try {
            result = generateFirstQuestion(interviewId, timeout);
        } catch (RuntimeException exception) {
            convergeModelFailure(interviewId, exception);
            throw exception;
        }

        return persistenceService.persistFirstQuestion(interviewId, result);
    }

    private void convergeModelFailure(UUID interviewId, RuntimeException exception) {
        try {
            persistenceService.failFirstQuestionGeneration(
                    interviewId,
                    failureCode(exception)
            );
        } catch (RuntimeException convergenceFailure) {
            exception.addSuppressed(convergenceFailure);
        }
    }

    private InterviewFailureCode failureCode(RuntimeException exception) {
        if (exception instanceof MockInterviewInputConflictException) {
            return InterviewFailureCode.INPUT_SNAPSHOT_UNAVAILABLE;
        }
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

    private void requireTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout必须大于0");
        }
    }
}