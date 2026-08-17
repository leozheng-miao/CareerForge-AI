package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapItem;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapStatus;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @program: CareerForge-AI
 * @description: 使用技能标准键和可信来源确定性生成安全保守的能力差距基线
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@Component
public class DeterministicSkillGapMatcher {
    public static final String ALGORITHM_VERSION = "deterministic-skill-gap-v1";

    public String algorithmVersion() {
        return ALGORITHM_VERSION;
    }

    public List<GapItem> match(TargetRole targetRole, ConfirmedSkillProfile profile) {
        Objects.requireNonNull(targetRole, "targetRole不能为空");
        Objects.requireNonNull(profile, "profile不能为空");
        if (!targetRole.ownerId().equals(profile.ownerId())) {
            throw new IllegalStateException("TargetRole与技能画像owner不一致");
        }
        Map<String, String> requirements = SkillGapRequirementCatalog.extract(targetRole.requirementsSnapshot());
        Map<String, List<MemoryItem>> evidenceBySkill = indexEvidence(profile.skillEvidence());
        List<GapItem> result = new ArrayList<>(requirements.size());
        requirements.forEach((reference, text) -> result.add(matchRequirement(reference, text, evidenceBySkill)));
        return List.copyOf(result);
    }

    private GapItem matchRequirement(String reference, String text, Map<String, List<MemoryItem>> evidenceBySkill) {
        String requirementKey = MemoryNormalizedKey.skillEvidence(text).value();
        List<MemoryItem> exactEvidence = evidenceBySkill.getOrDefault(requirementKey, List.of());
        List<MemoryItem> projectEvidence = exactEvidence.stream()
                .filter(memory -> memory.source().sourceType() == MemorySourceType.PROJECT_EVIDENCE)
                .toList();

        if (!projectEvidence.isEmpty()) {
            return createItem(reference, text, GapStatus.MATCHED, projectEvidence,
                    "存在同技能的已确认项目证据");
        }
        if (!exactEvidence.isEmpty()) {
            return createItem(reference, text, GapStatus.UNVERIFIED, exactEvidence,
                    "存在同技能的已确认自述，但缺少项目证据");
        }
        return new GapItem(UUID.randomUUID(), reference, text, GapStatus.MISSING, List.of(),
                "当前已确认技能画像中没有同技能证据");
    }

    private GapItem createItem(String reference, String text, GapStatus status,
                               List<MemoryItem> evidence, String reason) {
        if (evidence.size() > GapItem.MAX_EVIDENCE_COUNT) {
            throw new IllegalStateException("单项岗位要求匹配证据超过限制");
        }
        List<UUID> evidenceIds = evidence.stream().map(MemoryItem::memoryId).toList();
        return new GapItem(UUID.randomUUID(), reference, text, status, evidenceIds, reason);
    }

    private Map<String, List<MemoryItem>> indexEvidence(List<MemoryItem> evidence) {
        return evidence.stream()
                .sorted(Comparator.comparing(memory -> memory.memoryId().toString()))
                .collect(Collectors.groupingBy(
                        memory -> memory.normalizedKey().value(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }
}