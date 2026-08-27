package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射interview_round回合状态和乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
public class InterviewRoundEntity {

    private String roundId;
    private String interviewId;
    private String ownerId;
    private Integer roundNo;
    private String roundStatus;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant answeredAt;
    private Instant reviewedAt;
}