package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunExecutionApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAcceptanceApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimResult;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 编排Run认领、接受、同步执行、幂等重放和owner隔离查询
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingRunRepository repository;
    private final CoachingRunClaimApplicationService claimService;
    private final CoachingRunAcceptanceApplicationService acceptanceService;
    private final CoachingRunExecutionApplicationService executionService;

    public CoachingRunApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingRunRepository repository,
            CoachingRunClaimApplicationService claimService,
            CoachingRunAcceptanceApplicationService acceptanceService,
            CoachingRunExecutionApplicationService executionService
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.claimService = Objects.requireNonNull(claimService, "claimService不能为空");
        this.acceptanceService = Objects.requireNonNull(acceptanceService, "acceptanceService不能为空");
        this.executionService = Objects.requireNonNull(executionService, "executionService不能为空");
    }

    public CoachingRun submit(
            UUID sessionId,
            UUID requestId,
            long expectedSessionVersion,
            String message
    ) {
        // 调用Claim Service 创建 任务单
        CoachingRunClaimResult claimResult = claimService.claim(
                sessionId,
                requestId,
                expectedSessionVersion,
                message
        );
        // 如果已经有重复 runId， 直接返回现有的run
        if (claimResult.replayed()) return claimResult.run();

        CoachingRun accepted = acceptanceService.accept(claimResult.run().runId(), message);
        return executionService.execute(accepted.runId());
    }

    @Transactional(readOnly = true)
    public CoachingRun get(UUID runId) {
        Objects.requireNonNull(runId, "runId不能为空");
        ActorId ownerId = currentActorProvider.currentActor();
        return repository.findByRunId(ownerId, runId)
                .orElseThrow(() -> new CoachingRunNotFoundException(runId));
    }

    @Transactional(readOnly = true)
    public RunPage list(
            UUID sessionId,
            CoachingRunStatus status,
            String cursor,
            int limit
    ) {
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit必须在1到50之间");

        RunCursor decoded = decodeCursor(cursor);
        String statusKey = status == null ? "*" : status.name();
        if (decoded != null
                && (!decoded.sessionId().equals(sessionId)
                || !decoded.statusKey().equals(statusKey))) {
            throw new IllegalArgumentException("cursor与当前查询条件不匹配");
        }

        List<CoachingRun> rows = repository.findPage(
                currentActorProvider.currentActor(),
                sessionId,
                status,
                decoded == null ? null : decoded.createdAt(),
                decoded == null ? null : decoded.runId(),
                limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<CoachingRun> items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        return new RunPage(
                items,
                hasMore ? encodeCursor(items.getLast(), statusKey) : null,
                hasMore
        );
    }

    private static String encodeCursor(CoachingRun run, String statusKey) {
        String value = run.sessionId() + "|" + statusKey + "|" + run.createdAt() + "|" + run.runId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static RunCursor decodeCursor(String cursor) {
        if (cursor == null) return null;
        if (cursor.isBlank() || cursor.length() > 384) {
            throw new IllegalArgumentException("cursor格式不合法");
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8
            );
            String[] parts = value.split("\\|", -1);
            if (parts.length != 4 || parts[1].isBlank()) {
                throw new IllegalArgumentException("cursor格式不合法");
            }
            return new RunCursor(
                    UUID.fromString(parts[0]),
                    parts[1],
                    Instant.parse(parts[2]),
                    UUID.fromString(parts[3])
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor格式不合法");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: Coaching Run分页结果
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页Run
     * @param nextCursor 下一页Cursor
     * @param hasMore 是否存在下一页
     */
    public record RunPage(List<CoachingRun> items, String nextCursor, boolean hasMore) {
        public RunPage {
            items = List.copyOf(Objects.requireNonNull(items, "items不能为空"));
            if (hasMore != (nextCursor != null)) {
                throw new IllegalArgumentException("分页状态不一致");
            }
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 与Session和状态过滤绑定的Run分页位置
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param sessionId 查询Session
     * @param statusKey 状态过滤标识
     * @param createdAt 上一页最后创建时间
     * @param runId 上一页最后Run ID
     */
    private record RunCursor(
            UUID sessionId,
            String statusKey,
            Instant createdAt,
            UUID runId
    ) {
        private RunCursor {
            Objects.requireNonNull(sessionId, "sessionId不能为空");
            Objects.requireNonNull(statusKey, "statusKey不能为空");
            Objects.requireNonNull(createdAt, "createdAt不能为空");
            Objects.requireNonNull(runId, "runId不能为空");
        }
    }
}