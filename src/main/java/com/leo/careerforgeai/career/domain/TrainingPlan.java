package com.leo.careerforgeai.career.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示基于固定能力差距快照生成并由用户确认激活的训练计划
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param planId 训练计划UUID
 * @param ownerId 训练计划所属用户
 * @param planVersion 当前用户的训练计划业务版本
 * @param gapSnapshotId 生成计划时使用的能力差距快照ID
 * @param title 训练计划标题
 * @param status 当前计划状态
 * @param items 训练任务列表
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 * @param activatedAt 激活时间
 * @param completedAt 完成时间
 * @param cancelledAt 取消时间
 **/
public record TrainingPlan(
        UUID planId,
        ActorId ownerId,
        long planVersion,
        UUID gapSnapshotId,
        String title,
        PlanStatus status,
        List<TrainingPlanItem> items,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant completedAt,
        Instant cancelledAt
) {

    public static final int MAX_ITEMS = 100;
    public static final int MAX_TITLE_LENGTH = 120;

    public TrainingPlan {
        Objects.requireNonNull(planId, "planId 不能为空");
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(gapSnapshotId, "gapSnapshotId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");

        if (planVersion < 1) {
            throw new IllegalArgumentException("planVersion必须从1开始");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version不能小于0");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt不能早于createdAt");
        }

        title = normalizeTitle(title);
        items = normalizeItems(items);
        validateStatusTimes(status, createdAt, activatedAt, completedAt, cancelledAt);
        validateItemStatuses(status, items);
    }

    /**
     * 创建模型输出经过Java校验后的草案。
     * 模型不能传入计划状态、owner、版本和业务时间。
     */
    public static TrainingPlan createDraft(
            UUID planId,
            ActorId ownerId,
            long planVersion,
            UUID gapSnapshotId,
            String title,
            List<TrainingPlanItem> items,
            Instant now
    ) {
        return new TrainingPlan(
                planId,
                ownerId,
                planVersion,
                gapSnapshotId,
                title,
                PlanStatus.DRAFT,
                items,
                0,
                now,
                now,
                null,
                null,
                null
        );
    }

    /**
     * Java校验完成后提交给用户确认，重复提交保持幂等。
     */
    public TrainingPlan submitForConfirmation(Instant now) {
        requireValidOperationTime(now);

        if (status == PlanStatus.PENDING_CONFIRMATION) {
            return this;
        }
        if (status != PlanStatus.DRAFT) {
            throw new IllegalStateException("只有DRAFT计划可以提交确认");
        }

        return transitionTo(
                PlanStatus.PENDING_CONFIRMATION,
                items,
                version + 1,
                now,
                null,
                null,
                null
        );
    }

    /**
     * 用户确认后激活计划，模型不能调用该转换。
     */
    public TrainingPlan activate(Instant now) {
        requireValidOperationTime(now);

        if (status == PlanStatus.ACTIVE) {
            return this;
        }
        if (status != PlanStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("只有PENDING_CONFIRMATION计划可以激活");
        }

        return transitionTo(
                PlanStatus.ACTIVE,
                items,
                version + 1,
                now,
                now,
                null,
                null
        );
    }

    /**
     * 用户开始执行指定计划项。
     */
    public TrainingPlan startItem(UUID itemId, Instant now) {
        requireActive();
        requireValidOperationTime(now);

        TrainingPlanItem currentItem = findItem(itemId);
        TrainingPlanItem updatedItem = currentItem.start(now);

        if (currentItem == updatedItem) {
            return this;
        }

        return transitionTo(
                PlanStatus.ACTIVE,
                replaceItem(updatedItem),
                version + 1,
                now,
                activatedAt,
                null,
                null
        );
    }

    /**
     * 用户提交证据并完成指定计划项。
     */
    public TrainingPlan completeItem(UUID itemId, List<String> evidenceRefs, Instant now) {
        requireActive();
        requireValidOperationTime(now);

        TrainingPlanItem currentItem = findItem(itemId);
        TrainingPlanItem updatedItem = currentItem.complete(evidenceRefs, now);

        if (currentItem == updatedItem) {
            return this;
        }

        return transitionTo(
                PlanStatus.ACTIVE,
                replaceItem(updatedItem),
                version + 1,
                now,
                activatedAt,
                null,
                null
        );
    }

    /**
     * 所有计划项完成后，由用户操作或可信业务事件完成计划。
     */
    public TrainingPlan complete(Instant now) {
        requireValidOperationTime(now);

        if (status == PlanStatus.COMPLETED) {
            return this;
        }
        requireActive();

        if (items.stream().anyMatch(item -> item.status() != TrainingPlanItem.ItemStatus.COMPLETED)) {
            throw new IllegalStateException("所有计划项完成后才能完成训练计划");
        }

        return transitionTo(
                PlanStatus.COMPLETED,
                items,
                version + 1,
                now,
                activatedAt,
                now,
                null
        );
    }

    /**
     * 用户取消待确认或执行中的计划，重复取消保持幂等。
     */
    public TrainingPlan cancel(Instant now) {
        requireValidOperationTime(now);

        if (status == PlanStatus.CANCELLED) {
            return this;
        }
        if (status != PlanStatus.PENDING_CONFIRMATION && status != PlanStatus.ACTIVE) {
            throw new IllegalStateException("当前状态不允许取消训练计划");
        }

        return transitionTo(
                PlanStatus.CANCELLED,
                items,
                version + 1,
                now,
                activatedAt,
                null,
                now
        );
    }

    public int durationWeeks() {
        return items.stream().mapToInt(TrainingPlanItem::weekNumber).max().orElseThrow();
    }

    private TrainingPlan transitionTo(
            PlanStatus targetStatus,
            List<TrainingPlanItem> targetItems,
            long targetVersion,
            Instant targetUpdatedAt,
            Instant targetActivatedAt,
            Instant targetCompletedAt,
            Instant targetCancelledAt
    ) {
        return new TrainingPlan(
                planId,
                ownerId,
                planVersion,
                gapSnapshotId,
                title,
                targetStatus,
                targetItems,
                targetVersion,
                createdAt,
                targetUpdatedAt,
                targetActivatedAt,
                targetCompletedAt,
                targetCancelledAt
        );
    }

    private TrainingPlanItem findItem(UUID itemId) {
        Objects.requireNonNull(itemId, "itemId 不能为空");

        return items.stream()
                .filter(item -> item.itemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("计划项不存在"));
    }

    private List<TrainingPlanItem> replaceItem(TrainingPlanItem updatedItem) {
        List<TrainingPlanItem> updatedItems = new ArrayList<>(items.size());

        for (TrainingPlanItem item : items) {
            updatedItems.add(item.itemId().equals(updatedItem.itemId()) ? updatedItem : item);
        }

        return List.copyOf(updatedItems);
    }

    private void requireActive() {
        if (status != PlanStatus.ACTIVE) {
            throw new IllegalStateException("只有ACTIVE训练计划可以更新进度");
        }
    }

    private void requireValidOperationTime(Instant now) {
        Objects.requireNonNull(now, "now 不能为空");

        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("操作时间不能早于计划更新时间");
        }
    }

    private static List<TrainingPlanItem> normalizeItems(List<TrainingPlanItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items 不能为空");
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("items 数量超过限制");
        }

        Set<UUID> itemIds = new HashSet<>();
        Set<String> normalizedTitles = new HashSet<>();

        for (TrainingPlanItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("items 不能包含空值");
            }
            if (!itemIds.add(item.itemId())) {
                throw new IllegalArgumentException("itemId 不能重复");
            }

            String normalizedTitle = item.title().strip().toLowerCase(Locale.ROOT);

            if (!normalizedTitles.add(normalizedTitle)) {
                throw new IllegalArgumentException("训练计划不能包含重复任务");
            }
        }

        return List.copyOf(items);
    }

    private static void validateItemStatuses(PlanStatus status, List<TrainingPlanItem> items) {
        if ((status == PlanStatus.DRAFT || status == PlanStatus.PENDING_CONFIRMATION)
                && items.stream().anyMatch(item -> item.status() != TrainingPlanItem.ItemStatus.NOT_STARTED)) {
            throw new IllegalArgumentException("未激活计划的任务必须全部为NOT_STARTED");
        }

        if (status == PlanStatus.COMPLETED
                && items.stream().anyMatch(item -> item.status() != TrainingPlanItem.ItemStatus.COMPLETED)) {
            throw new IllegalArgumentException("COMPLETED计划的任务必须全部完成");
        }
    }

    private static void validateStatusTimes(
            PlanStatus status,
            Instant createdAt,
            Instant activatedAt,
            Instant completedAt,
            Instant cancelledAt
    ) {
        if (activatedAt != null && activatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("activatedAt不能早于createdAt");
        }
        if (completedAt != null && completedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("completedAt不能早于createdAt");
        }
        if (cancelledAt != null && cancelledAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("cancelledAt不能早于createdAt");
        }

        switch (status) {
            case DRAFT, PENDING_CONFIRMATION -> {
                if (activatedAt != null || completedAt != null || cancelledAt != null) {
                    throw new IllegalArgumentException(status + "计划不能包含终态时间");
                }
            }
            case ACTIVE -> {
                if (activatedAt == null || completedAt != null || cancelledAt != null) {
                    throw new IllegalArgumentException("ACTIVE计划的状态时间不合法");
                }
            }
            case COMPLETED -> {
                if (activatedAt == null || completedAt == null || cancelledAt != null) {
                    throw new IllegalArgumentException("COMPLETED计划的状态时间不合法");
                }
                if (completedAt.isBefore(activatedAt)) {
                    throw new IllegalArgumentException("completedAt不能早于activatedAt");
                }
            }
            case CANCELLED -> {
                if (completedAt != null || cancelledAt == null) {
                    throw new IllegalArgumentException("CANCELLED计划的状态时间不合法");
                }
            }
        }
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 不能为空");
        }

        String normalized = title.strip();

        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title 超过长度限制");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("title 不能包含控制字符");
        }

        return normalized;
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义训练计划从草案到完成或取消的生命周期状态
     * @author: Miao Zheng
     * @date: 2026-08-12
     **/
    public enum PlanStatus {

        /** 经过结构校验但尚未提交用户确认的草案。 */
        DRAFT,

        /** 等待用户确认，模型不能直接激活。 */
        PENDING_CONFIRMATION,

        /** 用户已经确认并允许更新任务进度。 */
        ACTIVE,

        /** 所有任务完成后由用户或可信业务事件确认完成。 */
        COMPLETED,

        /** 用户取消的待确认或活动计划。 */
        CANCELLED
    }
}