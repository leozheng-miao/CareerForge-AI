package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射interview_evidence_review不可变证据一致性评审事实
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
public class EvidenceReviewEntity {

    private String evidenceReviewId;
    private String interviewId;
    private String roundId;
    private String questionId;
    private String answerId;
    private String ownerId;
    private String reviewSource;
    private String verdict;
    private String evidenceReferenceIdsJson;
    private String reason;
    private String modelRequestId;
    private String promptVersion;
    private String inputHash;
    private String outputHash;
    private Instant createdAt;
}