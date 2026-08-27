package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射个人证据版本的稳定可引用片段
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
public class PersonalEvidenceChunkEntity {

    private String evidenceChunkId;
    private String artifactId;
    private Long artifactVersion;
    private String ownerId;
    private Integer chunkIndex;
    private Integer startOffset;
    private Integer endOffset;
    private String chunkContent;
    private String contentHash;
    private Instant createdAt;
}