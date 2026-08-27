package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射interview_technical_review不可变技术评审事实
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
public class TechnicalReviewEntity {

    private String technicalReviewId;
    private String interviewId;
    private String roundId;
    private String questionId;
    private String answerId;
    private String ownerId;
    private String dimensionScoresJson;
    private String coveredPointsJson;
    private String errorsOrOmissionsJson;
    private String verificationBasisJson;
    private String suggestedFollowUp;
    private String modelRequestId;
    private String promptVersion;
    private String inputHash;
    private String outputHash;
    private Instant createdAt;
}