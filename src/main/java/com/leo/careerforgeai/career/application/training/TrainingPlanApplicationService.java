package com.leo.careerforgeai.career.application.training;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * @program: CareerForge-AI
 * @description: 使用当前Actor和事务边界执行训练计划查询、激活、进度更新、完成与取消
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Service
@ConditionalOnBean(CareerPlanningRepository.class)
public class TrainingPlanApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CareerPlanningRepository repository;
    private final Clock clock;

    public TrainingPlanApplicationService(
            CurrentActorProvider currentActorProvider,
            CareerPlanningRepository repository,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider 不能为空");
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /** 查询属于当前用户的完整训练计划。 */
    @Transactional(readOnly = true)
    public TrainingPlan get(UUID planId) {
        ActorId actorId = currentActor();
        return requireOwnedPlan(actorId, planId);
    }

    /** 查询当前用户业务版本最高的训练计划。 */
    @Transactional(readOnly = true)
    public Optional<TrainingPlan> getLatest() {
        ActorId actorId = currentActor();
        return repository.findLatestTrainingPlan(actorId)
                .map(plan -> requireOwnedResult(actorId, plan));
    }
    /** 用户确认待确认计划，使其进入ACTIVE状态。 */
    @Transactional
    public TrainingPlan activate(UUID planId, long expectedVersion) {
        Instant now = clock.instant();
        return mutate(planId, expectedVersion, plan -> plan.activate(now));
    }

    /** 用户开始执行指定计划项。 */
    @Transactional
    public TrainingPlan startItem(UUID planId, long expectedVersion, UUID itemId) {
        Objects.requireNonNull(itemId, "itemId 不能为空");
        Instant now = clock.instant();
        return mutate(planId, expectedVersion, plan -> plan.startItem(itemId, now));
    }

    /** 用户提交受控证据引用并完成指定计划项。 */
    @Transactional
    public TrainingPlan completeItem(
            UUID planId,
            long expectedVersion,
            UUID itemId,
            List<String> evidenceRefs
    ) {
        Objects.requireNonNull(itemId, "itemId 不能为空");
        Instant now = clock.instant();
        return mutate(planId, expectedVersion, plan -> plan.completeItem(itemId, evidenceRefs, now));
    }

    /** 所有计划项完成后，由用户操作或可信业务事件完成训练计划。 */
    @Transactional
    public TrainingPlan complete(UUID planId, long expectedVersion) {
        Instant now = clock.instant();
        return mutate(planId, expectedVersion, plan -> plan.complete(now));
    }

    /** 用户取消待确认或活动中的训练计划。 */
    @Transactional
    public TrainingPlan cancel(UUID planId, long expectedVersion) {
        Instant now = clock.instant();
        return mutate(planId, expectedVersion, plan -> plan.cancel(now));
    }

    @Transactional(readOnly = true)
    public PlanPage list(TrainingPlan.PlanStatus status, String cursor, int limit) {
        if (limit < 1 || limit > 10) throw new IllegalArgumentException("limit必须在1到10之间");
        PlanCursor decoded = decodeCursor(cursor);
        String statusKey = status == null ? "*" : status.name();
        if (decoded != null && !decoded.statusKey().equals(statusKey)) {
            throw new IllegalArgumentException("cursor与当前状态过滤不匹配");
        }

        List<TrainingPlan> rows = repository.findTrainingPlanPage(
                currentActor(),
                status,
                decoded == null ? null : decoded.beforePlanVersion(),
                limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<TrainingPlan> items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        return new PlanPage(
                items,
                hasMore ? encodeCursor(items.getLast(), statusKey) : null,
                hasMore
        );
    }

    private static String encodeCursor(TrainingPlan plan, String statusKey) {
        String value = statusKey + "|" + plan.planVersion();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static PlanCursor decodeCursor(String cursor) {
        if (cursor == null) return null;
        if (cursor.isBlank() || cursor.length() > 128) throw invalidCursor();
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 2 || parts[0].isBlank()) throw invalidCursor();
            return new PlanCursor(parts[0], Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("cursor格式不合法");
    }

    /**
     * @program: CareerForge-AI
     * @description: 训练计划分页结果
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页计划
     * @param nextCursor 下一页Cursor
     * @param hasMore 是否存在下一页
     */
    public record PlanPage(List<TrainingPlan> items, String nextCursor, boolean hasMore) {
        public PlanPage {
            items = List.copyOf(Objects.requireNonNull(items, "items不能为空"));
            if (hasMore != (nextCursor != null)) throw new IllegalArgumentException("分页状态不一致");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 与状态过滤绑定的训练计划分页位置
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param statusKey 状态过滤标识
     * @param beforePlanVersion 下一页必须小于的业务版本
     */
    private record PlanCursor(String statusKey, long beforePlanVersion) {
        private PlanCursor {
            if (statusKey == null || statusKey.isBlank() || beforePlanVersion < 1) {
                throw invalidCursor();
            }
        }
    }

    private TrainingPlan mutate(
            UUID planId,
            long expectedVersion,
            UnaryOperator<TrainingPlan> mutation
    ) {
        Objects.requireNonNull(planId, "planId 不能为空");
        Objects.requireNonNull(mutation, "mutation 不能为空");

        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion不能小于0");
        }

        ActorId actorId = currentActor();
        TrainingPlan currentPlan = requireOwnedPlan(actorId, planId);
        TrainingPlan updatedPlan = mutation.apply(currentPlan);

        /*
         * 状态机返回同一个对象表示请求已经满足，例如重复激活或使用相同证据重复完成。
         * 此时不增加version，也不重复执行数据库UPDATE。
         */
        if (updatedPlan == currentPlan) {
            return currentPlan;
        }

        if (currentPlan.version() != expectedVersion) {
            throw new TrainingPlanVersionConflictException("训练计划版本已经过期");
        }

        boolean updated = repository.updateTrainingPlanIfVersionMatches(
                actorId,
                updatedPlan,
                expectedVersion
        );

        if (!updated) {
            throw new TrainingPlanVersionConflictException("训练计划并发更新冲突");
        }

        return updatedPlan;
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor 不能为空");
    }

    private TrainingPlan requireOwnedPlan(ActorId actorId, UUID planId) {
        Objects.requireNonNull(planId, "planId不能为空");
        TrainingPlan plan = repository.findTrainingPlan(actorId, planId)
                .orElseThrow(() -> new IllegalArgumentException("训练计划不存在或不属于当前用户"));
        return requireOwnedResult(actorId, plan);
    }

    private TrainingPlan requireOwnedResult(ActorId actorId, TrainingPlan plan) {
        if (!actorId.equals(plan.ownerId())) {
            throw new IllegalStateException("TrainingPlan查询结果违反owner边界");
        }
        return plan;
    }
}