package com.leo.careerforgeai.interview.application.model.contract;

import com.leo.careerforgeai.interview.domain.InterviewRole;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 集中维护四个面试角色稳定且不依赖模型框架的JSON Schema
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public class InterviewRoleJsonSchemas {

    private static final String INTERVIEW_QUESTION = """
            {
              "$schema":"https://json-schema.org/draft/2020-12/schema",
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "questionType":{"type":"string","enum":["TECHNICAL_KNOWLEDGE","PROJECT_DEEP_DIVE","SYSTEM_DESIGN"]},
                "question":{"type":"string","minLength":1,"maxLength":2000},
                "targetSkills":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"string","minLength":1,"maxLength":100}},
                "difficulty":{"type":"integer","minimum":1,"maximum":5},
                "evaluationPoints":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"string","minLength":1,"maxLength":500}},
                "followUpAllowed":{"type":"boolean"},
                "evidenceReferenceIds":{"type":"array","maxItems":10,"items":{"type":"string","pattern":"^[0-9a-f]{64}$"}}
              },
              "required":["questionType","question","targetSkills","difficulty","evaluationPoints","followUpAllowed","evidenceReferenceIds"]
            }
            """;

    private static final String TECHNICAL_REVIEW = """
            {
              "$schema":"https://json-schema.org/draft/2020-12/schema",
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "dimensionScores":{
                  "type":"object",
                  "minProperties":1,
                  "maxProperties":10,
                  "propertyNames":{"pattern":"^[A-Z][A-Z0-9_]{0,63}$"},
                  "additionalProperties":{"type":"integer","minimum":0,"maximum":5}
                },
                "coveredPoints":{"type":"array","maxItems":20,"items":{"type":"string","minLength":1,"maxLength":500}},
                "errorsOrOmissions":{"type":"array","maxItems":20,"items":{"type":"string","minLength":1,"maxLength":500}},
                "verificationBasis":{"type":"array","maxItems":20,"items":{"type":"string","minLength":1,"maxLength":500}},
                "suggestedFollowUp":{"type":"string","maxLength":2000}
              },
              "required":["dimensionScores","coveredPoints","errorsOrOmissions","verificationBasis","suggestedFollowUp"]
            }
            """;

    private static final String EVIDENCE_REVIEW = """
            {
              "$schema":"https://json-schema.org/draft/2020-12/schema",
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "verdict":{"type":"string","enum":["SUPPORTED","PARTIALLY_SUPPORTED","UNSUPPORTED","CONTRADICTED","NOT_APPLICABLE"]},
                "evidenceReferenceIds":{"type":"array","maxItems":10,"items":{"type":"string","pattern":"^[0-9a-f]{64}$"}},
                "reason":{"type":"string","minLength":1,"maxLength":2000}
              },
              "required":["verdict","evidenceReferenceIds","reason"]
            }
            """;

    private static final String INTERVIEW_REPORT = """
            {
              "$schema":"https://json-schema.org/draft/2020-12/schema",
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "strengths":{"type":"array","maxItems":20,"items":{"type":"string","minLength":1,"maxLength":1000}},
                "technicalGaps":{"type":"array","maxItems":20,"items":{"type":"string","minLength":1,"maxLength":1000}},
                "evidenceExpressionRisks":{"type":"array","maxItems":20,"items":{"type":"string","minLength":1,"maxLength":1000}},
                "improvementActions":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"string","minLength":1,"maxLength":1000}},
                "proposedMemoryCandidates":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":1000}},
                "proposedTrainingPlanAdjustments":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":1000}}
              },
              "required":["strengths","technicalGaps","evidenceExpressionRisks","improvementActions","proposedMemoryCandidates","proposedTrainingPlanAdjustments"]
            }
            """;

    private InterviewRoleJsonSchemas() {
    }

    public static String outputSchema(InterviewRole role, Class<?> outputType) {
        Objects.requireNonNull(role, "role不能为空");
        Objects.requireNonNull(outputType, "outputType不能为空");
        Class<?> expectedType = expectedOutputType(role);
        if (outputType != expectedType) {
            throw new IllegalArgumentException(
                    "角色输出类型不匹配，role=" + role + "，expected=" + expectedType.getSimpleName()
            );
        }
        return switch (role) {
            case INTERVIEWER -> INTERVIEW_QUESTION;
            case TECHNICAL_REVIEWER -> TECHNICAL_REVIEW;
            case EVIDENCE_REVIEWER -> EVIDENCE_REVIEW;
            case REPORT_COACH -> INTERVIEW_REPORT;
        };
    }

    private static Class<?> expectedOutputType(InterviewRole role) {
        return switch (role) {
            case INTERVIEWER -> InterviewQuestionDraft.class;
            case TECHNICAL_REVIEWER -> TechnicalReviewDraft.class;
            case EVIDENCE_REVIEWER -> EvidenceReviewDraft.class;
            case REPORT_COACH -> InterviewReportDraft.class;
        };
    }
}