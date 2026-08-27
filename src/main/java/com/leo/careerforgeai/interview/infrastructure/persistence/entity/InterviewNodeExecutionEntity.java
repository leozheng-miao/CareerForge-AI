
package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射interview_node_execution节点执行身份、状态、模型用量和版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
public class InterviewNodeExecutionEntity {

    private String executionId;
    private String interviewId;
    private String ownerId;
    private Integer roundNo;
    private String nodeName;
    private String inputHash;
    private String executionStatus;
    private String outputReferenceId;
    private String modelRequestId;
    private Integer attemptCount;
    private Integer modelCallCount;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long modelDurationMs;
    private String failureCode;
    private Long version;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
    private Instant updatedAt;
}