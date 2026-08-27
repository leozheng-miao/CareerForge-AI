package com.leo.careerforgeai.interview.application.evidence;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示个人证据期望版本过期或生命周期CAS竞争失败
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public final class PersonalEvidenceVersionConflictException extends RuntimeException {

    private final UUID artifactId;
    private final long expectedVersion;

    public PersonalEvidenceVersionConflictException(UUID artifactId, long expectedVersion) {
        super("个人证据版本冲突");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId不能为空");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion必须从1开始");
        this.expectedVersion = expectedVersion;
    }

    public UUID artifactId() {
        return artifactId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}