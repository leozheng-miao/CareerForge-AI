package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射interview_question不可变问题及模型生成来源
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
public class InterviewQuestionEntity {

    private String questionId;
    private String interviewId;
    private String roundId;
    private String ownerId;
    private String parentQuestionId;
    private String questionType;
    private String questionText;
    private Integer difficulty;
    private String targetSkillsJson;
    private String evaluationPointsJson;
    private Boolean followUpAllowed;
    private Boolean followUp;
    private String evidenceRefsJson;
    private String modelRequestId;
    private String promptVersion;
    private String contentHash;
    private Instant createdAt;
}