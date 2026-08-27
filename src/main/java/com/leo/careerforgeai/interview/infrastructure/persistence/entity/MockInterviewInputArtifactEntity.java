package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射模拟面试输入快照引用的个人证据版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
public class MockInterviewInputArtifactEntity {

    private String inputSnapshotId;
    private String ownerId;
    private String artifactId;
    private Long artifactVersion;
    private String artifactSourceHash;
    private Integer artifactOrder;
    private Instant createdAt;
}