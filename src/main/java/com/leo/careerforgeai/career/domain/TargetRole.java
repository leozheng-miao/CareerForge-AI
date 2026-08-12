package com.leo.careerforgeai.career.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 表示用户确认后不可原地修改的目标岗位版本及其岗位要求快照
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param targetRoleId 目标岗位记录的服务端UUID
 * @param ownerId 目标岗位所属用户
 * @param targetRoleVersion 当前用户的目标岗位业务版本，从1开始递增
 * @param sourceRef 原始JD的受控来源标识
 * @param sourceHash 原始JD正文的小写SHA-256
 * @param parserVersion 生成岗位要求草案时使用的解析器版本
 * @param promptVersion 生成岗位要求草案时使用的Prompt版本
 * @param requirementsSnapshot 用户确认后冻结的岗位要求快照
 * @param confirmedAt 用户确认时间
 **/
public record TargetRole(
        UUID targetRoleId,
        ActorId ownerId,
        long targetRoleVersion,
        String sourceRef,
        String sourceHash,
        String parserVersion,
        String promptVersion,
        JobRequirements requirementsSnapshot,
        Instant confirmedAt
) {

    public static final int MAX_SOURCE_REF_LENGTH = 128;
    public static final int MAX_COMPONENT_VERSION_LENGTH = 64;

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public TargetRole {
        Objects.requireNonNull(targetRoleId, "targetRoleId 不能为空");
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(requirementsSnapshot, "requirementsSnapshot 不能为空");
        Objects.requireNonNull(confirmedAt, "confirmedAt 不能为空");

        if (targetRoleVersion < 1) {
            throw new IllegalArgumentException("targetRoleVersion必须从1开始");
        }

        sourceRef = normalizeRequired(sourceRef, "sourceRef", MAX_SOURCE_REF_LENGTH);
        sourceHash = normalizeSourceHash(sourceHash);
        parserVersion = normalizeRequired(parserVersion, "parserVersion", MAX_COMPONENT_VERSION_LENGTH);
        promptVersion = normalizeRequired(promptVersion, "promptVersion", MAX_COMPONENT_VERSION_LENGTH);

        if (requirementsSnapshot.jobTitle() == null || requirementsSnapshot.jobTitle().isBlank()) {
            throw new IllegalArgumentException("岗位要求快照必须包含jobTitle");
        }
    }

    /**
     * 根据已经得到用户确认的岗位要求草案创建冻结版本。
     * ID、owner、业务版本和确认时间必须由服务端提供，不能采用模型输出。
     */
    public static TargetRole createConfirmed(
            UUID targetRoleId,
            ActorId ownerId,
            long targetRoleVersion,
            String sourceRef,
            String sourceHash,
            String parserVersion,
            String promptVersion,
            JobRequirements requirementsSnapshot,
            Instant confirmedAt
    ) {
        return new TargetRole(
                targetRoleId,
                ownerId,
                targetRoleVersion,
                sourceRef,
                sourceHash,
                parserVersion,
                promptVersion,
                requirementsSnapshot,
                confirmedAt
        );
    }

    private static String normalizeSourceHash(String sourceHash) {
        String normalized = normalizeRequired(sourceHash, "sourceHash", 64);

        if (!SHA_256_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("sourceHash必须是小写SHA-256");
        }

        return normalized;
    }

    private static String normalizeRequired(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }

        String normalized = value.strip();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 超过长度限制");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + " 不能包含控制字符");
        }

        return normalized;
    }
}