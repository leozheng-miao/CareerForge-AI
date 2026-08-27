package com.leo.careerforgeai.interview.domain;

import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存Graph副作用节点的逻辑身份、执行状态、模型用量和CAS版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param executionId 节点执行UUID
 * @param interviewId 所属面试UUID
 * @param ownerId 所属用户
 * @param roundNo 回合号，面试级节点使用0
 * @param nodeName Graph节点名称
 * @param inputHash 节点冻结输入的小写SHA-256
 * @param status 当前执行状态
 * @param outputReferenceId 成功后持久化业务事实的引用ID
 * @param modelRequestId 最近一次模型请求ID
 * @param attemptCount 节点执行权获取次数
 * @param modelCallCount 节点累计模型调用次数
 * @param modelUsage 节点累计Token用量
 * @param modelDurationMs 节点累计模型调用耗时
 * @param failureCode 当前或最近一次稳定失败码
 * @param version 乐观锁版本
 * @param startedAt 当前执行尝试开始时间
 * @param finishedAt 当前执行尝试结束时间
 * @param createdAt 首次创建时间
 * @param updatedAt 更新时间
 **/
public record InterviewNodeExecution(
        UUID executionId,
        UUID interviewId,
        ActorId ownerId,
        int roundNo,
        String nodeName,
        String inputHash,
        InterviewNodeExecutionStatus status,
        String outputReferenceId,
        String modelRequestId,
        int attemptCount,
        int modelCallCount,
        ModelUsage modelUsage,
        long modelDurationMs,
        String failureCode,
        long version,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public InterviewNodeExecution {
        Objects.requireNonNull(executionId, "executionId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(modelUsage, "modelUsage不能为空");
        Objects.requireNonNull(startedAt, "startedAt不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");

        if (roundNo < 0) throw new IllegalArgumentException("roundNo不能小于0");
        requireText(nodeName, "nodeName", 64);
        inputHash = requireSha256(inputHash, "inputHash");
        if (attemptCount < 1) throw new IllegalArgumentException("attemptCount必须从1开始");
        if (modelCallCount < 0 || modelCallCount > 2) {
            throw new IllegalArgumentException("modelCallCount必须在0到2之间");
        }
        if (modelDurationMs < 0) throw new IllegalArgumentException("modelDurationMs不能小于0");
        if (version < 0) throw new IllegalArgumentException("version不能小于0");
        if (startedAt.isBefore(createdAt)) throw new IllegalArgumentException("startedAt不能早于createdAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt不能早于createdAt");

        validateUsage(modelCallCount, modelRequestId, modelUsage, modelDurationMs);
        validateLifecycle(status, outputReferenceId, failureCode, startedAt, finishedAt);

        if (outputReferenceId != null) requireText(outputReferenceId, "outputReferenceId", 128);
        if (failureCode != null) requireText(failureCode, "failureCode", 64);
    }

    private static void validateUsage(
            int modelCallCount,
            String modelRequestId,
            ModelUsage usage,
            long durationMs
    ) {
        if (usage.inputTokens() < 0 || usage.outputTokens() < 0 || usage.totalTokens() < 0) {
            throw new IllegalArgumentException("模型Token用量不能小于0");
        }

        long expectedTotal;
        try {
            expectedTotal = Math.addExact(usage.inputTokens(), usage.outputTokens());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("模型Token用量超出允许范围", exception);
        }
        if (usage.totalTokens() != expectedTotal) {
            throw new IllegalArgumentException("totalTokens必须等于inputTokens加outputTokens");
        }

        if (modelCallCount == 0) {
            if (modelRequestId != null || usage.totalTokens() != 0 || durationMs != 0) {
                throw new IllegalArgumentException("无模型调用时不能包含模型请求、Token或耗时");
            }
        } else {
            requireText(modelRequestId, "modelRequestId", 128);
        }
    }

    private static void validateLifecycle(
            InterviewNodeExecutionStatus status,
            String outputReferenceId,
            String failureCode,
            Instant startedAt,
            Instant finishedAt
    ) {
        switch (status) {
            case RUNNING -> {
                if (outputReferenceId != null || finishedAt != null) {
                    throw new IllegalArgumentException("RUNNING不能包含输出引用或结束时间");
                }
            }
            case SUCCEEDED -> {
                if (outputReferenceId == null || finishedAt == null) {
                    throw new IllegalArgumentException("SUCCEEDED必须包含输出引用和结束时间");
                }
            }
            case FAILED -> {
                if (outputReferenceId != null || failureCode == null || finishedAt == null) {
                    throw new IllegalArgumentException("FAILED必须包含失败码和结束时间且不能包含输出引用");
                }
            }
        }
        if (finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt不能早于startedAt");
        }
    }

    private static void requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能为空且长度不能超过" + maxLength);
        }
    }

    private static String requireSha256(String value, String fieldName) {
        if (value == null || !SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "必须是64位小写SHA-256");
        }
        return value;
    }
}