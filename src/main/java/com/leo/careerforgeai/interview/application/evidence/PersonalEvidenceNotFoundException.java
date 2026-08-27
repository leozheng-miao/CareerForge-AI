package com.leo.careerforgeai.interview.application.evidence;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示当前用户范围内不存在指定个人证据或证据版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public final class PersonalEvidenceNotFoundException extends RuntimeException {

    private final UUID artifactId;

    public PersonalEvidenceNotFoundException(UUID artifactId) {
        super("个人证据不存在");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId不能为空");
    }

    public UUID artifactId() {
        return artifactId;
    }
}