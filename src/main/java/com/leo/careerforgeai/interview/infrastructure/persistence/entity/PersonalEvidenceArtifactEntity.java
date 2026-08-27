package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射个人证据不可变正文版本及生命周期字段
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
public class PersonalEvidenceArtifactEntity {

    private String artifactId;
    private Long artifactVersion;
    private String ownerId;
    private String artifactType;
    private String sourceName;
    private String sourceHash;
    private String content;
    private String artifactStatus;
    private Long supersededByVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant supersededAt;
    private Instant revokedAt;
}