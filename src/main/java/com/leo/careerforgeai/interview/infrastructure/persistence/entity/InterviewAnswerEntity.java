package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射interview_answer用户不可变答案及幂等请求身份
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
public class InterviewAnswerEntity {

    private String answerId;
    private String interviewId;
    private String roundId;
    private String questionId;
    private String ownerId;
    private String requestId;
    private String requestFingerprint;
    private Long expectedInterviewVersion;
    private String answerText;
    private String contentHash;
    private Instant submittedAt;
}