package com.leo.careerforgeai.memory.domain.extraction;

import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示一次已经成功完成并可幂等回放的Memory提取凭证
 * @author: Miao Zheng
 * @date: 2026-08-14
 * @param receiptId 服务端生成的凭证ID
 * @param ownerId 凭证所属用户
 * @param inputIdentity 提取输入的稳定幂等身份
 * @param memoryIds 本次产生或复用的Memory ID，允许为空
 * @param modelRequestId 成功模型调用的请求ID
 * @param modelUsage 本次提取全部模型调用的Token用量
 * @param modelDurationMs 本次提取全部模型调用和校验耗时
 * @param modelCallCount 本次提取实际模型调用次数
 * @param createdAt 凭证创建时间
 **/
public record MemoryExtractionReceipt(
        UUID receiptId,
        ActorId ownerId,
        MemoryExtractionInputIdentity inputIdentity,
        List<UUID> memoryIds,
        String modelRequestId,
        ModelUsage modelUsage,
        long modelDurationMs,
        int modelCallCount,
        Instant createdAt
) {

    public static final int MAX_MEMORY_RESULTS = 10;

    public MemoryExtractionReceipt {
        Objects.requireNonNull(receiptId, "receiptId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(inputIdentity, "inputIdentity不能为空");
        Objects.requireNonNull(memoryIds, "memoryIds不能为空");
        Objects.requireNonNull(modelUsage, "modelUsage不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");

        if (memoryIds.size() > MAX_MEMORY_RESULTS
                || memoryIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(memoryIds).size() != memoryIds.size()) {
            throw new IllegalArgumentException("memoryIds数量、内容或重复关系不合法");
        }

        memoryIds = List.copyOf(memoryIds);
        modelRequestId = normalizeModelRequestId(modelRequestId);

        if (modelUsage.inputTokens() < 0
                || modelUsage.outputTokens() < 0
                || modelUsage.totalTokens() < 0) {
            throw new IllegalArgumentException("modelUsage不能包含负数");
        }
        if (modelDurationMs < 0) {
            throw new IllegalArgumentException("modelDurationMs不能小于0");
        }
        if (modelCallCount < 1 || modelCallCount > 2) {
            throw new IllegalArgumentException("modelCallCount必须在1到2之间");
        }
    }

    private static String normalizeModelRequestId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("modelRequestId不能为空");
        }

        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 128
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("modelRequestId格式不合法");
        }
        return normalized;
    }
}