package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.blueprint.InterviewBlueprintApplicationService;
import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionInput;
import com.leo.careerforgeai.interview.application.model.validation.InterviewRoleContractException;
import com.leo.careerforgeai.interview.application.model.validation.InterviewQuestionRoleContract;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 从冻结输入生成并幂等持久化首题、下一题或技术追问
 * @author: Miao Zheng
 * @date: 2026-08-29
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
        return generateAndPersistQuestion(interviewId, 1, null, timeout);
    }

    public InterviewQuestion generateAndPersistQuestion(
            UUID interviewId,
            int roundNo,
            InterviewRouteDecision routeDecision,
            Duration timeout
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        requireTimeout(timeout);
        if (roundNo < 1) throw new IllegalArgumentException("roundNo必须从1开始");
        if (roundNo == 1 && routeDecision != null) {
            throw new IllegalArgumentException("首题不能包含后续路由");
        }
        if (roundNo > 1
                && routeDecision != InterviewRouteDecision.FOLLOW_UP
                && routeDecision != InterviewRouteDecision.NEXT_QUESTION) {
            throw new IllegalArgumentException("后续问题必须来自FOLLOW_UP或NEXT_QUESTION");
        }

        Optional<InterviewQuestion> existing =
                persistenceService.startQuestionGeneration(interviewId, roundNo);
        if (existing.isPresent()) return existing.get();

        InterviewQuestionInput input;
        InterviewRoleModelGateway.Result<InterviewQuestionDraft> result;
        try {
            input = roundNo == 1
                    ? blueprintService.prepareFirstQuestion(interviewId)
                    : blueprintService.prepareNextQuestion(interviewId, routeDecision);
            result = modelGateway.generate(questionContract, input, timeout);
        } catch (RuntimeException exception) {
            convergeFailure(interviewId, exception);
            throw exception;
        }

        try {
            return persistenceService.persistQuestion(
                    interviewId,
                    input,
                    routeDecision,
                    result
            );
        } catch (InterviewRoleContractException exception) {
            convergeFailure(interviewId, exception);
            throw exception;
        }
    }

    private void convergeFailure(UUID interviewId, RuntimeException exception) {
        try {
            persistenceService.failQuestionGeneration(interviewId, failureCode(exception));
        } catch (RuntimeException convergenceFailure) {
            exception.addSuppressed(convergenceFailure);
        }
    }

    private InterviewFailureCode failureCode(RuntimeException exception) {
        if (exception instanceof MockInterviewInputConflictException) {
            return InterviewFailureCode.INPUT_SNAPSHOT_UNAVAILABLE;
        }
        if (exception instanceof InterviewRoleContractException) {
            return InterviewFailureCode.MODEL_OUTPUT_INVALID;
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