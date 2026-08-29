package com.leo.careerforgeai.interview.application.model;

import com.leo.careerforgeai.interview.application.model.contract.EvidenceReviewDraft;
import com.leo.careerforgeai.interview.application.model.contract.EvidenceReviewInput;
import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionInput;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportInput;
import com.leo.careerforgeai.interview.application.model.contract.InterviewRoleContract;
import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewDraft;
import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewInput;
import com.leo.careerforgeai.interview.application.model.validation.EvidenceReviewRoleContract;
import com.leo.careerforgeai.interview.application.model.validation.InterviewQuestionRoleContract;
import com.leo.careerforgeai.interview.application.model.validation.InterviewReportRoleContract;
import com.leo.careerforgeai.interview.application.model.validation.InterviewRoleContractErrorType;
import com.leo.careerforgeai.interview.application.model.validation.InterviewRoleContractException;
import com.leo.careerforgeai.interview.application.model.validation.TechnicalReviewRoleContract;
import com.leo.careerforgeai.interview.domain.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewQuestionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportSuggestionDraft;

/**
 * @program: CareerForge-AI
 * @description: 验证四个模型角色的Schema、引用白名单、评分维度和跨字段规则
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterviewRoleContractTest {

    private static final String CHUNK_A = "a".repeat(64);
    private static final String CHUNK_B = "b".repeat(64);

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = validatorFactory.getValidator();
    private final InterviewQuestionRoleContract questionContract = new InterviewQuestionRoleContract(validator);
    private final TechnicalReviewRoleContract technicalContract = new TechnicalReviewRoleContract(validator);
    private final EvidenceReviewRoleContract evidenceContract = new EvidenceReviewRoleContract(validator);
    private final InterviewReportRoleContract reportContract = new InterviewReportRoleContract(validator);

    @AfterAll
    void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldGenerateClosedJsonSchemaForEveryRole() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();

        for (InterviewRoleContract<?, ?> contract : List.of(
                questionContract,
                technicalContract,
                evidenceContract,
                reportContract
        )) {
            var schema = jsonMapper.readTree(contract.outputJsonSchema());
            assertThat(schema.path("type").asText()).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
            assertThat(schema.path("properties").isObject()).isTrue();
        }
    }

    @Test
    void shouldRejectQuestionThatChangesBlueprintOrUsesUnauthorizedEvidence() {
        InterviewQuestionInput input = questionInput();
        InterviewQuestionDraft valid = new InterviewQuestionDraft(
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                "解释数据库事务隔离级别。",
                List.of("MYSQL"),
                3,
                List.of("说明隔离级别与并发现象"),
                true,
                List.of(CHUNK_A)
        );

        assertThat(questionContract.validateOutput(input, valid)).isSameAs(valid);

        InterviewQuestionDraft invalid = new InterviewQuestionDraft(
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                valid.question(),
                valid.targetSkills(),
                3,
                valid.evaluationPoints(),
                true,
                List.of(CHUNK_B)
        );
        InterviewRoleContractException exception = catchThrowableOfType(
                () -> questionContract.validateOutput(input, invalid),
                InterviewRoleContractException.class
        );

        assertThat(exception.errorType()).isEqualTo(InterviewRoleContractErrorType.REFERENCE_NOT_ALLOWED);
    }

    @Test
    void shouldRequireExactTechnicalScoreDimensions() {
        TechnicalReviewInput input = technicalInput();
        TechnicalReviewDraft valid = new TechnicalReviewDraft(
                Map.of("CORRECTNESS", 4, "DEPTH", 3),
                List.of("说明了隔离级别"),
                List.of("未说明幻读差异"),
                List.of("回答与评分Rubric对照"),
                "请补充可重复读与串行化的差异。"
        );

        assertThat(technicalContract.validateOutput(input, valid)).isSameAs(valid);

        TechnicalReviewDraft invalid = new TechnicalReviewDraft(
                Map.of("CORRECTNESS", 4),
                valid.coveredPoints(),
                valid.errorsOrOmissions(),
                valid.verificationBasis(),
                valid.suggestedFollowUp()
        );
        InterviewRoleContractException exception = catchThrowableOfType(
                () -> technicalContract.validateOutput(input, invalid),
                InterviewRoleContractException.class
        );

        assertThat(exception.errorType()).isEqualTo(InterviewRoleContractErrorType.SCORE_DIMENSION_MISMATCH);
    }

    @Test
    void shouldEnforceEvidenceApplicabilityAndReferenceWhitelist() {
        EvidenceReviewInput noEvidenceInput = new EvidenceReviewInput(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "解释事务隔离级别。",
                "这是纯技术知识回答。",
                Map.of()
        );
        EvidenceReviewDraft notApplicable = new EvidenceReviewDraft(
                EvidenceConsistencyVerdict.NOT_APPLICABLE,
                List.of(),
                "该问题不涉及个人项目或经历证据。"
        );

        assertThat(evidenceContract.validateOutput(noEvidenceInput, notApplicable)).isSameAs(notApplicable);

        EvidenceReviewInput evidenceInput = new EvidenceReviewInput(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "介绍项目中的并发控制。",
                "项目使用了乐观锁。",
                Map.of(CHUNK_A, "项目材料记录了乐观锁实现。")
        );
        EvidenceReviewDraft invalid = new EvidenceReviewDraft(
                EvidenceConsistencyVerdict.SUPPORTED,
                List.of(CHUNK_B),
                "回答得到材料支持。"
        );
        InterviewRoleContractException exception = catchThrowableOfType(
                () -> evidenceContract.validateOutput(evidenceInput, invalid),
                InterviewRoleContractException.class
        );

        assertThat(exception.errorType()).isEqualTo(InterviewRoleContractErrorType.REFERENCE_NOT_ALLOWED);
    }

    @Test
    void shouldRejectDuplicatedReportSuggestions() {
        InterviewReportInput input = new InterviewReportInput(
                UUID.randomUUID(),
                "Java后端与Agent应用开发工程师",
                List.of("第一轮回答与评审摘要")
        );
        InterviewReportDraft valid = new InterviewReportDraft(
                List.of("并发基础清晰"),
                List.of("事务隔离理解不完整"),
                List.of(),
                List.of("补充事务隔离实验"),
                List.of(new InterviewReportSuggestionDraft.MemoryCandidate(
                        "Java并发",
                        "具备基础并发能力"
                )),
                List.of(new InterviewReportSuggestionDraft.TrainingPlanAdjustment(
                        "事务隔离",
                        "增加事务隔离训练项"
                ))
        );

        assertThat(reportContract.validateOutput(input, valid)).isSameAs(valid);

        InterviewReportDraft invalid = new InterviewReportDraft(
                valid.strengths(),
                valid.technicalGaps(),
                valid.evidenceExpressionRisks(),
                valid.improvementActions(),
                List.of(
                        new InterviewReportSuggestionDraft.MemoryCandidate(
                                "Java 并发",
                                "具备基础并发能力"
                        ),
                        new InterviewReportSuggestionDraft.MemoryCandidate(
                                "java 并发",
                                "能够说明虚拟线程边界"
                        )
                ),
                valid.proposedTrainingPlanAdjustments()
        );
        InterviewRoleContractException exception = catchThrowableOfType(
                () -> reportContract.validateOutput(input, invalid),
                InterviewRoleContractException.class
        );

        assertThat(exception.errorType()).isEqualTo(InterviewRoleContractErrorType.OUTPUT_INVALID);
    }
    private InterviewQuestionInput questionInput() {
        return new InterviewQuestionInput(
                UUID.randomUUID(),
                1,
                InterviewMode.TARGETED_MOCK,
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                3,
                "本轮覆盖数据库事务。",
                "Java后端工程师，需要掌握MySQL。",
                Map.of(CHUNK_A, "项目材料记录了MySQL事务与并发控制实践。"),
                List.of(),
                "验证事务隔离和并发异常理解。"
        );
    }

    private TechnicalReviewInput technicalInput() {
        return new TechnicalReviewInput(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "解释数据库事务隔离级别。",
                "隔离级别用于控制并发事务之间的可见性。",
                List.of("掌握MySQL事务与并发"),
                List.of("CORRECTNESS", "DEPTH"),
                List.of("CORRECTNESS检查事实正确性", "DEPTH检查分析深度")
        );
    }
}