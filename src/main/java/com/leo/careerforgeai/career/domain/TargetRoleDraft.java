package com.leo.careerforgeai.career.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 表示模型解析后等待当前用户审阅确认的目标岗位草案
 * @author: Miao Zheng
 * @date: 2026-08-16
 * @param draftId 服务端生成的草案UUID
 * @param ownerId 草案所属用户
 * @param sourceRef 原始JD的受控来源标识
 * @param sourceHash 原始JD正文的小写SHA-256
 * @param parserVersion 岗位解析器版本
 * @param promptVersion 岗位解析Prompt版本
 * @param requirementsSnapshot 等待用户审阅的结构化岗位要求
 * @param status 草案状态，本切片只能为PENDING
 * @param version 草案乐观锁版本，本切片固定为0
 * @param createdAt 草案创建时间
 * @param confirmedTargetRoleId 确认后创建的不可变TargetRole ID
 * @param confirmedTargetRoleVersion 确认后创建的TargetRole业务版本
 * @param confirmedAt 用户确认时间
 */
public record TargetRoleDraft(
        UUID draftId,
        ActorId ownerId,
        String sourceRef,
        String sourceHash,
        String parserVersion,
        String promptVersion,
        JobRequirements requirementsSnapshot,
        Status status,
        long version,
        Instant createdAt,
        UUID confirmedTargetRoleId,
        Long confirmedTargetRoleVersion,
        Instant confirmedAt
) {

    public static final int MAX_SOURCE_REF_LENGTH = 128;
    public static final int MAX_COMPONENT_VERSION_LENGTH = 64;

    private static final Pattern SHA_256_PATTERN =
            Pattern.compile("[0-9a-f]{64}");

    public TargetRoleDraft {
        Objects.requireNonNull(draftId, "draftId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(requirementsSnapshot, "requirementsSnapshot不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");

        sourceRef = normalizeRequired(
                sourceRef, "sourceRef", MAX_SOURCE_REF_LENGTH
        );
        sourceHash = normalizeSourceHash(sourceHash);
        parserVersion = normalizeRequired(
                parserVersion, "parserVersion", MAX_COMPONENT_VERSION_LENGTH
        );
        promptVersion = normalizeRequired(
                promptVersion, "promptVersion", MAX_COMPONENT_VERSION_LENGTH
        );

        if (requirementsSnapshot.jobTitle() == null
                || requirementsSnapshot.jobTitle().isBlank()) {
            throw new IllegalArgumentException("岗位要求草案必须包含jobTitle");
        }
        validateLifecycle(
                status,
                version,
                createdAt,
                confirmedTargetRoleId,
                confirmedTargetRoleVersion,
                confirmedAt
        );
    }

    public static TargetRoleDraft createPending(
            UUID draftId,
            ActorId ownerId,
            String sourceRef,
            String sourceHash,
            String parserVersion,
            String promptVersion,
            JobRequirements requirementsSnapshot,
            Instant createdAt
    ) {
        return new TargetRoleDraft(
                draftId,
                ownerId,
                sourceRef,
                sourceHash,
                parserVersion,
                promptVersion,
                requirementsSnapshot,
                Status.PENDING,
                0,
                createdAt,
                null,
                null,
                null
        );
    }

    private static String normalizeSourceHash(String sourceHash) {
        String normalized = normalizeRequired(
                sourceHash,
                "sourceHash",
                64
        );

        if (!SHA_256_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "sourceHash必须是小写SHA-256"
            );
        }
        return normalized;
    }

    private static String normalizeRequired(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "不能为空"
            );
        }

        String normalized = value.strip();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "超过长度限制"
            );
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    fieldName + "不能包含控制字符"
            );
        }
        return normalized;
    }

    private static void validateLifecycle(
            Status status,
            long version,
            Instant createdAt,
            UUID confirmedTargetRoleId,
            Long confirmedTargetRoleVersion,
            Instant confirmedAt
    ) {
        switch (status) {
            case PENDING -> {
                if (version != 0) {
                    throw new IllegalArgumentException(
                            "PENDING目标岗位草案version必须为0"
                    );
                }
                if (confirmedTargetRoleId != null
                        || confirmedTargetRoleVersion != null
                        || confirmedAt != null) {
                    throw new IllegalArgumentException(
                            "PENDING目标岗位草案不能包含确认结果"
                    );
                }
            }
            case CONFIRMED -> {
                if (version != 1) {
                    throw new IllegalArgumentException(
                            "CONFIRMED目标岗位草案version必须为1"
                    );
                }
                Objects.requireNonNull(
                        confirmedTargetRoleId,
                        "CONFIRMED草案必须包含targetRoleId"
                );
                Objects.requireNonNull(
                        confirmedTargetRoleVersion,
                        "CONFIRMED草案必须包含targetRoleVersion"
                );
                Objects.requireNonNull(
                        confirmedAt,
                        "CONFIRMED草案必须包含confirmedAt"
                );

                if (confirmedTargetRoleVersion < 1) {
                    throw new IllegalArgumentException(
                            "confirmedTargetRoleVersion必须从1开始"
                    );
                }
                if (confirmedAt.isBefore(createdAt)) {
                    throw new IllegalArgumentException(
                            "confirmedAt不能早于createdAt"
                    );
                }
            }
        }
    }

    /**
     * 用户确认当前草案后绑定服务端创建的不可变TargetRole。
     * 该方法不能修改来源、解析版本或要求快照。
     */
    public TargetRoleDraft confirm(
            TargetRole targetRole,
            Instant confirmedAt
    ) {
        Objects.requireNonNull(targetRole, "targetRole不能为空");
        Objects.requireNonNull(confirmedAt, "confirmedAt不能为空");

        validateTargetRole(targetRole);

        if (status == Status.CONFIRMED) {
            if (confirmedTargetRoleId.equals(
                    targetRole.targetRoleId()
            ) && confirmedTargetRoleVersion.equals(
                    targetRole.targetRoleVersion()
            )) {
                return this;
            }
            throw new IllegalStateException(
                    "目标岗位草案已经绑定其他TargetRole"
            );
        }

        return new TargetRoleDraft(
                draftId,
                ownerId,
                sourceRef,
                sourceHash,
                parserVersion,
                promptVersion,
                requirementsSnapshot,
                Status.CONFIRMED,
                version + 1,
                createdAt,
                targetRole.targetRoleId(),
                targetRole.targetRoleVersion(),
                confirmedAt
        );
    }

    private void validateTargetRole(TargetRole targetRole) {
        boolean inconsistent =
                !ownerId.equals(targetRole.ownerId())
                        || !sourceRef.equals(targetRole.sourceRef())
                        || !sourceHash.equals(targetRole.sourceHash())
                        || !parserVersion.equals(
                        targetRole.parserVersion()
                )
                        || !promptVersion.equals(
                        targetRole.promptVersion()
                )
                        || !requirementsSnapshot.equals(
                        targetRole.requirementsSnapshot()
                );

        if (inconsistent) {
            throw new IllegalArgumentException(
                    "TargetRole与目标岗位草案事实不一致"
            );
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义目标岗位草案尚未得到用户确认的状态
     * @author: Miao Zheng
     * @date: 2026-08-16
     */
    public enum Status {
        PENDING,
        CONFIRMED
    }
}