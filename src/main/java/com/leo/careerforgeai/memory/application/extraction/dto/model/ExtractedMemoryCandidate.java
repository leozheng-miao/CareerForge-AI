package com.leo.careerforgeai.memory.application.extraction.dto.model;

import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示通过结构、来源白名单和keyHint业务校验后的Memory候选
 * @author: Miao Zheng
 * @date: 2026-08-13
 * @param type 已校验的Memory业务类型
 * @param normalizedKey Java确定性生成的冲突槽位或技能分组键
 * @param content 经过基础安全校验的候选正文
 * @param sourceTurnId 已通过输入白名单校验的主要来源Turn
 * @param evidenceTurnIds 已通过输入白名单和重复校验的证据Turn
 * @param confidence 模型自评置信度，只能用于诊断或排序
 **/
public record ExtractedMemoryCandidate(
        MemoryType type,
        MemoryNormalizedKey normalizedKey,
        String content,
        UUID sourceTurnId,
        List<UUID> evidenceTurnIds,
        BigDecimal confidence
) {

    public ExtractedMemoryCandidate {
        Objects.requireNonNull(type, "type不能为空");
        Objects.requireNonNull(normalizedKey, "normalizedKey不能为空");
        Objects.requireNonNull(sourceTurnId, "sourceTurnId不能为空");
        Objects.requireNonNull(evidenceTurnIds, "evidenceTurnIds不能为空");
        Objects.requireNonNull(confidence, "confidence不能为空");

        if (!normalizedKey.supports(type)) {
            throw new IllegalArgumentException(
                    "normalizedKey与Memory类型不匹配"
            );
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content不能为空");
        }

        content = content.strip();

        if (content.length() > MemoryItem.MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content超过长度限制");
        }
        if (content.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("content不能包含控制字符");
        }

        if (evidenceTurnIds.isEmpty()
                || evidenceTurnIds.size() > MemoryItem.MAX_EVIDENCE_REFS) {
            throw new IllegalArgumentException(
                    "evidenceTurnIds数量不合法"
            );
        }
        if (evidenceTurnIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "evidenceTurnIds不能包含空值"
            );
        }

        LinkedHashSet<UUID> distinctEvidence =
                new LinkedHashSet<>(evidenceTurnIds);

        if (distinctEvidence.size() != evidenceTurnIds.size()) {
            throw new IllegalArgumentException(
                    "evidenceTurnIds不能重复"
            );
        }
        if (!distinctEvidence.contains(sourceTurnId)) {
            throw new IllegalArgumentException(
                    "主要来源必须同时存在于证据列表"
            );
        }

        evidenceTurnIds = List.copyOf(distinctEvidence);

        if (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "confidence必须在0到1之间"
            );
        }
    }
}