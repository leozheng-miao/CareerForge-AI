package com.leo.careerforgeai.career.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示训练计划中的具体任务、关联差距、验收要求和确定性进度
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param itemId 计划项UUID
 * @param weekNumber 所属周次，从1开始
 * @param title 任务标题
 * @param taskDescription 具体任务说明
 * @param estimatedMinutes 预计投入分钟数
 * @param completionCriteria 完成标准
 * @param evidenceRequirement 用户完成任务时需要提交的证据说明
 * @param gapItemIds 关联的能力差距明细ID
 * @param foundationGoal 未关联Gap时的基础准备目标
 * @param resourceRefs 受控学习资源引用
 * @param status 当前任务进度
 * @param completionEvidenceRefs 用户实际提交的证据引用
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 **/
public record TrainingPlanItem(
        UUID itemId,
        int weekNumber,
        String title,
        String taskDescription,
        int estimatedMinutes,
        String completionCriteria,
        String evidenceRequirement,
        List<UUID> gapItemIds,
        String foundationGoal,
        List<ResourceRef> resourceRefs,
        ItemStatus status,
        List<String> completionEvidenceRefs,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static final int MAX_WEEK_NUMBER = 52;
    public static final int MAX_ESTIMATED_MINUTES = 10_080;
    public static final int MAX_GAP_REFS = 20;
    public static final int MAX_RESOURCE_REFS = 20;
    public static final int MAX_EVIDENCE_REFS = 20;

    public TrainingPlanItem {
        Objects.requireNonNull(itemId, "itemId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");

        if (weekNumber < 1 || weekNumber > MAX_WEEK_NUMBER) {
            throw new IllegalArgumentException("weekNumber超出允许范围");
        }
        if (estimatedMinutes < 1 || estimatedMinutes > MAX_ESTIMATED_MINUTES) {
            throw new IllegalArgumentException("estimatedMinutes超出允许范围");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version不能小于0");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt不能早于createdAt");
        }

        title = normalizeRequired(title, "title", 120);
        taskDescription = normalizeRequired(taskDescription, "taskDescription", 2_000);
        completionCriteria = normalizeRequired(completionCriteria, "completionCriteria", 1_000);
        evidenceRequirement = normalizeRequired(evidenceRequirement, "evidenceRequirement", 1_000);
        foundationGoal = normalizeOptional(foundationGoal, "foundationGoal", 500);
        gapItemIds = normalizeUuidRefs(gapItemIds, "gapItemIds", MAX_GAP_REFS);
        resourceRefs = normalizeResourceRefs(resourceRefs);
        completionEvidenceRefs = normalizeStringRefs(
                completionEvidenceRefs,
                "completionEvidenceRefs",
                MAX_EVIDENCE_REFS
        );

        if (gapItemIds.isEmpty() && foundationGoal == null) {
            throw new IllegalArgumentException("计划项必须关联Gap或明确的基础准备目标");
        }
        if (status == ItemStatus.COMPLETED && completionEvidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("COMPLETED计划项必须包含完成证据");
        }
        if (status != ItemStatus.COMPLETED && !completionEvidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("未完成计划项不能包含完成证据");
        }
    }

    /**
     * 创建尚未开始的计划项，模型不能指定任务进度和完成证据。
     */
    public static TrainingPlanItem createDraft(
            UUID itemId,
            int weekNumber,
            String title,
            String taskDescription,
            int estimatedMinutes,
            String completionCriteria,
            String evidenceRequirement,
            List<UUID> gapItemIds,
            String foundationGoal,
            List<ResourceRef> resourceRefs,
            Instant now
    ) {
        return new TrainingPlanItem(
                itemId,
                weekNumber,
                title,
                taskDescription,
                estimatedMinutes,
                completionCriteria,
                evidenceRequirement,
                gapItemIds,
                foundationGoal,
                resourceRefs,
                ItemStatus.NOT_STARTED,
                List.of(),
                0,
                now,
                now
        );
    }

    /**
     * 由用户操作把任务推进为进行中，重复请求保持幂等。
     */
    public TrainingPlanItem start(Instant now) {
        requireValidOperationTime(now);

        if (status == ItemStatus.IN_PROGRESS) {
            return this;
        }
        if (status != ItemStatus.NOT_STARTED) {
            throw new IllegalStateException("只有NOT_STARTED计划项可以开始");
        }

        return new TrainingPlanItem(
                itemId,
                weekNumber,
                title,
                taskDescription,
                estimatedMinutes,
                completionCriteria,
                evidenceRequirement,
                gapItemIds,
                foundationGoal,
                resourceRefs,
                ItemStatus.IN_PROGRESS,
                List.of(),
                version + 1,
                createdAt,
                now
        );
    }

    /**
     * 由用户提交证据并完成任务，模型不能自行调用该状态转换。
     */
    public TrainingPlanItem complete(List<String> evidenceRefs, Instant now) {
        requireValidOperationTime(now);
        List<String> normalizedEvidenceRefs = normalizeStringRefs(
                evidenceRefs,
                "evidenceRefs",
                MAX_EVIDENCE_REFS
        );

        if (normalizedEvidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("完成计划项必须提交证据");
        }
        if (status == ItemStatus.COMPLETED) {
            if (completionEvidenceRefs.equals(normalizedEvidenceRefs)) {
                return this;
            }
            throw new IllegalStateException("已完成计划项不能替换完成证据");
        }

        return new TrainingPlanItem(
                itemId,
                weekNumber,
                title,
                taskDescription,
                estimatedMinutes,
                completionCriteria,
                evidenceRequirement,
                gapItemIds,
                foundationGoal,
                resourceRefs,
                ItemStatus.COMPLETED,
                normalizedEvidenceRefs,
                version + 1,
                createdAt,
                now
        );
    }

    private void requireValidOperationTime(Instant now) {
        Objects.requireNonNull(now, "now 不能为空");

        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("操作时间不能早于计划项更新时间");
        }
    }

    private static List<UUID> normalizeUuidRefs(List<UUID> refs, String fieldName, int maxSize) {
        if (refs == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        if (refs.size() > maxSize) {
            throw new IllegalArgumentException(fieldName + " 数量超过限制");
        }

        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();

        for (UUID ref : refs) {
            if (ref == null) {
                throw new IllegalArgumentException(fieldName + " 不能包含空值");
            }
            if (!normalized.add(ref)) {
                throw new IllegalArgumentException(fieldName + " 不能重复");
            }
        }

        return List.copyOf(normalized);
    }

    private static List<ResourceRef> normalizeResourceRefs(List<ResourceRef> resourceRefs) {
        if (resourceRefs == null) {
            throw new IllegalArgumentException("resourceRefs 不能为空");
        }
        if (resourceRefs.size() > MAX_RESOURCE_REFS) {
            throw new IllegalArgumentException("resourceRefs 数量超过限制");
        }
        if (resourceRefs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("resourceRefs 不能包含空值");
        }
        if (new LinkedHashSet<>(resourceRefs).size() != resourceRefs.size()) {
            throw new IllegalArgumentException("resourceRefs 不能重复");
        }

        return List.copyOf(resourceRefs);
    }

    private static List<String> normalizeStringRefs(List<String> refs, String fieldName, int maxSize) {
        if (refs == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        if (refs.size() > maxSize) {
            throw new IllegalArgumentException(fieldName + " 数量超过限制");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String ref : refs) {
            String normalizedRef = normalizeRequired(ref, fieldName, 200);

            if (!normalized.add(normalizedRef)) {
                throw new IllegalArgumentException(fieldName + " 不能重复");
            }
        }

        return List.copyOf(normalized);
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

    private static String normalizeOptional(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return normalizeRequired(value, fieldName, maxLength);
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义训练计划项的确定性进度
     * @author: Miao Zheng
     * @date: 2026-08-12
     **/
    public enum ItemStatus {

        /** 尚未开始。 */
        NOT_STARTED,

        /** 用户已经开始执行。 */
        IN_PROGRESS,

        /** 用户提交证据后确认完成。 */
        COMPLETED
    }

    /**
     * @program: CareerForge-AI
     * @description: 表示计划项引用的受控知识资源
     * @author: Miao Zheng
     * @date: 2026-08-12
     * @param resourceType 资源类型
     * @param resourceId 本次计划输入白名单中的资源ID
     **/
    public record ResourceRef(ResourceType resourceType, String resourceId) {

        public ResourceRef {
            Objects.requireNonNull(resourceType, "resourceType 不能为空");
            resourceId = normalizeRequired(resourceId, "resourceId", 200);

            if (resourceType == ResourceType.KNOWLEDGE_CHUNK
                    && !resourceId.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("KNOWLEDGE_CHUNK的resourceId必须是小写SHA-256");
            }
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义训练计划可引用的知识资源类型
     * @author: Miao Zheng
     * @date: 2026-08-12
     **/
    public enum ResourceType {

        /** 阶段二知识库中的完整来源文档。 */
        KNOWLEDGE_DOCUMENT,

        /** 阶段二知识库中的具体文本片段。 */
        KNOWLEDGE_CHUNK
    }
}