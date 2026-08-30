package com.leo.careerforgeai.interview.application.report;

import com.leo.careerforgeai.interview.application.model.contract.InterviewReportDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportInput;
import com.leo.careerforgeai.interview.application.model.validation.InterviewReportRoleContract;
import com.leo.careerforgeai.interview.application.model.validation.InterviewRoleContractException;
import com.leo.careerforgeai.interview.application.port.InterviewReportFactory;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewNodeExecution;
import com.leo.careerforgeai.interview.domain.InterviewReport;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * @program: CareerForge-AI
 * @description: 认领报告执行权并调用Report Coach生成和持久化待确认报告
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
@ConditionalOnBean({
        InterviewReportPreparationService.class,
        InterviewReportRoleContract.class,
        InterviewRoleModelGateway.class,
        InterviewReportPersistenceService.class
})
public class InterviewReportGenerationService {

    private final InterviewReportPreparationService preparationService;
    private final InterviewReportRoleContract reportContract;
    private final InterviewRoleModelGateway modelGateway;
    private final InterviewReportPersistenceService persistenceService;
    private final InterviewReportFactory reportFactory;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public InterviewReportGenerationService(
            InterviewReportPreparationService preparationService,
            InterviewReportRoleContract reportContract,
            InterviewRoleModelGateway modelGateway,
            InterviewReportPersistenceService persistenceService,
            InterviewReportFactory reportFactory,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.preparationService = Objects.requireNonNull(preparationService, "preparationService不能为空");
        this.reportContract = Objects.requireNonNull(reportContract, "reportContract不能为空");
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway不能为空");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService不能为空");
        this.reportFactory = Objects.requireNonNull(reportFactory, "reportFactory不能为空");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public InterviewReport generateAndPersist(UUID interviewId, Duration timeout) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        requireTimeout(timeout);

        InterviewReportInput input = preparationService.prepare(interviewId);
        String inputHash = inputHash(input);
        InterviewReportPersistenceService.Claim claim =
                persistenceService.claimGeneration(interviewId, inputHash);
        if (claim.completed()) return claim.report();

        InterviewNodeExecution execution = claim.execution();
        try {
            InterviewRoleModelGateway.Result<InterviewReportDraft> result =
                    modelGateway.generate(reportContract, input, timeout);
            InterviewReport candidate = reportFactory.create(
                    UUID.randomUUID(),
                    interviewId,
                    execution.ownerId(),
                    inputHash,
                    result,
                    clock.instant()
            );
            return persistenceService.persist(candidate, execution, result);
        } catch (RuntimeException exception) {
            convergeFailure(execution, exception);
            throw exception;
        }
    }

    private String inputHash(InterviewReportInput input) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("schemaVersion", 1);
        fingerprint.put("node", InterviewReportPersistenceService.GENERATE_REPORT_NODE);
        fingerprint.put("input", input);

        try {
            return sha256(jsonMapper.writeValueAsString(fingerprint));
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化报告输入Hash失败", exception);
        }
    }

    private void convergeFailure(InterviewNodeExecution execution, RuntimeException exception) {
        try {
            persistenceService.fail(execution, failureCode(exception));
        } catch (RuntimeException convergenceFailure) {
            exception.addSuppressed(convergenceFailure);
        }
    }

    private InterviewFailureCode failureCode(RuntimeException exception) {
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