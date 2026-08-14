package com.leo.careerforgeai.memory.domain.profile;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示具有用户归属、来源、状态和版本的长期Memory候选或历史记录
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param memoryId 服务端生成的稳定Memory标识
 * @param ownerId Memory所属用户，不接受客户端自由指定
 * @param type Memory业务类型
 * @param normalizedKey 冲突槽位或技能分组键
 * @param content 经过长度校验的Memory正文
 * @param contentHash Memory正文的小写SHA-256
 * @param status 当前确认生命周期状态
 * @param source Memory的可追溯来源
 * @param extractionModelRequestId 产生该候选的模型请求ID，非模型候选允许为空
 * @param extractionConfidence 模型自评置信度，只用于候选审计或排序
 * @param sourceAgentRunId 主要来源Turn关联的Agent Run ID，用户Turn来源时为空
 * @param evidenceRefs 关联的对话、文档或项目证据ID
 * @param supersedesId 当前Memory准备替代的旧Memory ID
 * @param version 乐观锁版本，新建时为0
 * @param createdAt 创建时间
 * @param updatedAt 最后状态更新时间
 **/
public record MemoryItem(
        UUID memoryId,
        ActorId ownerId,
        MemoryType type,
        MemoryNormalizedKey normalizedKey,
        String content,
        String contentHash,
        MemoryStatus status,
        MemorySource source,
        String extractionModelRequestId,
        BigDecimal extractionConfidence,
        String sourceAgentRunId,
        List<String> evidenceRefs,
        UUID supersedesId,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static final int MAX_CONTENT_LENGTH = 2_000;
    public static final int MAX_EVIDENCE_REFS = 20;

    private static final int MAX_EVIDENCE_REF_LENGTH = 128;

    public MemoryItem {
        Objects.requireNonNull(memoryId, "memoryId 不能为空");
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(type, "type 不能为空");
        Objects.requireNonNull(normalizedKey, "normalizedKey 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(source, "source 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");

        if (!normalizedKey.supports(type)) {
            throw new IllegalArgumentException(
                    "normalizedKey 与Memory类型不匹配"
            );
        }

        content = normalizeContent(content);
        String expectedContentHash = calculateContentHash(content);

        if (!expectedContentHash.equals(contentHash)) {
            throw new IllegalArgumentException(
                    "contentHash 与Memory正文不一致"
            );
        }

        evidenceRefs = normalizeEvidenceRefs(evidenceRefs);

        if (supersedesId != null && supersedesId.equals(memoryId)) {
            throw new IllegalArgumentException(
                    "Memory不能替代自身"
            );
        }
        if (version < 0) {
            throw new IllegalArgumentException(
                    "version 不能小于0"
            );
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "updatedAt 不能早于createdAt"
            );
        }

        extractionModelRequestId = normalizeOptional(
                extractionModelRequestId,
                "extractionModelRequestId",
                128
        );

        sourceAgentRunId = normalizeOptional(sourceAgentRunId, "sourceAgentRunId", 128);
        if (extractionConfidence != null
                && (extractionConfidence.compareTo(BigDecimal.ZERO) < 0
                || extractionConfidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("extractionConfidence必须在0到1之间");
        }
    }

    /**
     * 创建普通PENDING候选。
     * 模型只能提供候选内容，owner、状态、ID和时间均由服务端传入。
     */
    public static MemoryItem createPending(
            UUID memoryId,
            ActorId ownerId,
            MemoryType type,
            MemoryNormalizedKey normalizedKey,
            String content,
            MemorySource source,
            List<String> evidenceRefs,
            Instant now
    ) {
        String normalizedContent = normalizeContent(content);

        return new MemoryItem(
                memoryId,
                ownerId,
                type,
                normalizedKey,
                normalizedContent,
                calculateContentHash(normalizedContent),
                MemoryStatus.PENDING,
                source,
                null,
                null,
                null,
                evidenceRefs,
                null,
                0,
                now,
                now
        );
    }

    /**
     * 创建准备替代旧Memory的PENDING候选。
     * 旧Memory必须已经确认，新候选继承同一用户、类型和冲突槽位。
     */
    public static MemoryItem createPendingReplacement(
            UUID memoryId,
            MemoryItem existingMemory,
            String content,
            MemorySource source,
            List<String> evidenceRefs,
            Instant now
    ) {
        Objects.requireNonNull(
                existingMemory,
                "existingMemory 不能为空"
        );

        if (existingMemory.status() != MemoryStatus.CONFIRMED) {
            throw new IllegalArgumentException(
                    "只能替代CONFIRMED Memory"
            );
        }

        String normalizedContent = normalizeContent(content);

        return new MemoryItem(
                memoryId,
                existingMemory.ownerId(),
                existingMemory.type(),
                existingMemory.normalizedKey(),
                normalizedContent,
                calculateContentHash(normalizedContent),
                MemoryStatus.PENDING,
                source,
                null,
                null,
                null,
                evidenceRefs,
                existingMemory.memoryId(),
                0,
                now,
                now
        );
    }

    /**
     * 创建经过Extractor校验的PENDING候选。
     * 模型请求ID只用于来源审计，不能作为用户确认依据。
     */
    public static MemoryItem createExtractedPending(
            UUID memoryId,
            ActorId ownerId,
            MemoryType type,
            MemoryNormalizedKey normalizedKey,
            String content,
            MemorySource source,
            String extractionModelRequestId,
            BigDecimal extractionConfidence,
            String sourceAgentRunId,
            List<String> evidenceRefs,
            Instant now
    ) {
        Objects.requireNonNull(source, "source不能为空");
        Objects.requireNonNull(extractionConfidence, "extractionConfidence不能为空");
        if (source.sourceType() != MemorySourceType.CONVERSATION_TURN) {
            throw new IllegalArgumentException("会话提取候选必须来源于Conversation Turn");
        }
        if (extractionModelRequestId == null || extractionModelRequestId.isBlank()) {
            throw new IllegalArgumentException("extractionModelRequestId不能为空");
        }

        String normalizedContent = normalizeContent(content);
        return new MemoryItem(
                memoryId,
                ownerId,
                type,
                normalizedKey,
                normalizedContent,
                calculateContentHash(normalizedContent),
                MemoryStatus.PENDING,
                source,
                extractionModelRequestId,
                extractionConfidence,
                sourceAgentRunId,
                evidenceRefs,
                null,
                0,
                now,
                now
        );
    }

    /**
     * 创建经过Extractor校验并准备显式替代旧Memory的PENDING候选。
     * 旧Memory只用于继承服务端owner、类型和槽位，模型不能指定supersedesId。
     */
    public static MemoryItem createExtractedPendingReplacement(
            UUID memoryId,
            MemoryItem existingMemory,
            String content,
            MemorySource source,
            String extractionModelRequestId,
            BigDecimal extractionConfidence,
            String sourceAgentRunId,
            List<String> evidenceRefs,
            Instant now
    ) {
        Objects.requireNonNull(existingMemory, "existingMemory不能为空");
        Objects.requireNonNull(source, "source不能为空");
        Objects.requireNonNull(extractionConfidence, "extractionConfidence不能为空");

        if (existingMemory.status() != MemoryStatus.CONFIRMED) {
            throw new IllegalArgumentException("只能为CONFIRMED Memory创建替代候选");
        }
        if (source.sourceType() != MemorySourceType.CONVERSATION_TURN) {
            throw new IllegalArgumentException("会话提取候选必须来源于Conversation Turn");
        }
        if (extractionModelRequestId == null || extractionModelRequestId.isBlank()) {
            throw new IllegalArgumentException("extractionModelRequestId不能为空");
        }

        String normalizedContent = normalizeContent(content);
        return new MemoryItem(
                memoryId,
                existingMemory.ownerId(),
                existingMemory.type(),
                existingMemory.normalizedKey(),
                normalizedContent,
                calculateContentHash(normalizedContent),
                MemoryStatus.PENDING,
                source,
                extractionModelRequestId,
                extractionConfidence,
                sourceAgentRunId,
                evidenceRefs,
                existingMemory.memoryId(),
                0,
                now,
                now
        );
    }

    /**
     * 应用已经完成业务校验的用户决策，返回新版本Memory。
     */
    public MemoryItem applyDecision(MemoryDecision decision) {
        Objects.requireNonNull(decision, "decision 不能为空");

        if (!memoryId.equals(decision.memoryId()) || !ownerId.equals(decision.ownerId())) {
            throw new IllegalArgumentException("决策不属于当前Memory");
        }
        if (version != decision.expectedMemoryVersion()) {
            throw new IllegalArgumentException("决策使用了过期Memory版本");
        }
        if (status != decision.fromStatus()) {
            throw new IllegalArgumentException("决策起始状态与当前Memory不一致");
        }
        if (decision.decidedAt().isBefore(updatedAt)) {
            throw new IllegalArgumentException("决策时间不能早于Memory更新时间");
        }

        MemoryStatus targetStatus = MemoryStateMachine.transition(status, decision.decisionType());

        if (targetStatus != decision.toStatus()) {
            throw new IllegalArgumentException("决策目标状态不一致");
        }

        return new MemoryItem(
                memoryId,
                ownerId,
                type,
                normalizedKey,
                content,
                contentHash,
                targetStatus,
                source,
                extractionModelRequestId,
                extractionConfidence,
                sourceAgentRunId,
                evidenceRefs,
                supersedesId,
                version + 1,
                createdAt,
                decision.decidedAt()
        );
    }

    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "content 不能为空"
            );
        }

        String normalized = content.strip();

        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                    "content 超过长度限制"
            );
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "content 不能包含控制字符"
            );
        }

        return normalized;
    }

    private static List<String> normalizeEvidenceRefs(
            List<String> evidenceRefs
    ) {
        if (evidenceRefs == null) {
            throw new IllegalArgumentException(
                    "evidenceRefs 不能为空"
            );
        }
        if (evidenceRefs.size() > MAX_EVIDENCE_REFS) {
            throw new IllegalArgumentException(
                    "evidenceRefs 数量超过限制"
            );
        }

        LinkedHashSet<String> normalizedRefs = new LinkedHashSet<>();

        for (String evidenceRef : evidenceRefs) {
            if (evidenceRef == null || evidenceRef.isBlank()) {
                throw new IllegalArgumentException(
                        "evidenceRefs 不能包含空值"
                );
            }

            String normalizedRef = evidenceRef.strip();

            if (normalizedRef.length() > MAX_EVIDENCE_REF_LENGTH) {
                throw new IllegalArgumentException(
                        "evidenceRef 超过长度限制"
                );
            }
            if (normalizedRef.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(
                        "evidenceRef 不能包含控制字符"
                );
            }
            if (!normalizedRefs.add(normalizedRef)) {
                throw new IllegalArgumentException(
                        "evidenceRefs 不能重复"
                );
            }
        }

        return List.copyOf(normalizedRefs);
    }

    private static String normalizeOptional(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空字符串");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "超过长度限制");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + "不能包含控制字符");
        }
        return normalized;
    }

    private static String calculateContentHash(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JDK不支持SHA-256",
                    exception
            );
        }
    }
}