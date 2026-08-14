package com.leo.careerforgeai.memory.api.dto;

import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回供当前用户审阅的Memory候选及必要来源信息
 * @author: Miao Zheng
 * @date: 2026-08-13
 * @param memoryId 服务端生成的Memory标识
 * @param type Memory业务类型
 * @param normalizedKey Java生成的业务槽位
 * @param content 待用户审阅的候选正文
 * @param status 当前候选状态
 * @param sourceType 来源类型
 * @param sourceId 主要来源Turn ID
 * @param evidenceRefs 证据Turn ID列表
 * @param extractionConfidence 模型自评置信度，不代表用户确认
 * @param version 后续确认操作使用的乐观锁版本
 * @param createdAt 候选创建时间
 **/
public record MemoryCandidateResponse(
        UUID memoryId,
        MemoryType type,
        String normalizedKey,
        String content,
        MemoryStatus status,
        MemorySourceType sourceType,
        String sourceId,
        List<String> evidenceRefs,
        BigDecimal extractionConfidence,
        long version,
        Instant createdAt
) {

    public static MemoryCandidateResponse from(MemoryItem item) {
        return new MemoryCandidateResponse(
                item.memoryId(),
                item.type(),
                item.normalizedKey().value(),
                item.content(),
                item.status(),
                item.source().sourceType(),
                item.source().sourceId(),
                item.evidenceRefs(),
                item.extractionConfidence(),
                item.version(),
                item.createdAt()
        );
    }
}