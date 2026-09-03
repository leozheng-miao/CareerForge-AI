package com.leo.careerforgeai.career.api.training;

import com.leo.careerforgeai.career.api.dto.training.CompleteTrainingPlanItemRequest;
import com.leo.careerforgeai.career.api.dto.training.TrainingPlanVersionRequest;
import com.leo.careerforgeai.career.api.dto.training.GenerateTrainingPlanRequest;
import com.leo.careerforgeai.career.api.dto.training.TrainingPlanResponse;
import com.leo.careerforgeai.career.application.training.TrainingPlanApplicationService;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerationApplicationService;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供训练计划草案生成和当前用户受控查询API
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
@RestController
@RequestMapping("/api/training-plans")
@Tag(name = "Training Plan")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class TrainingPlanController {

    private final TrainingPlanGenerationApplicationService generationService;
    private final TrainingPlanApplicationService planService;

    public TrainingPlanController(
            TrainingPlanGenerationApplicationService generationService,
            TrainingPlanApplicationService planService
    ) {
        this.generationService = Objects.requireNonNull(generationService, "generationService不能为空");
        this.planService = Objects.requireNonNull(planService, "planService不能为空");
    }

    @PostMapping
    @Operation(summary = "基于当前用户选择的Gap快照生成待确认训练计划")
    public BaseResponse<TrainingPlanResponse> generate(
            @Valid @RequestBody GenerateTrainingPlanRequest request
    ) {
        return ResultUtils.success(
                TrainingPlanResponse.from(generationService.generate(request.gapSnapshotId()))
        );
    }

    @GetMapping("/{planId}")
    @Operation(summary = "查询当前用户指定训练计划")
    public BaseResponse<TrainingPlanResponse> get(@PathVariable UUID planId) {
        return ResultUtils.success(TrainingPlanResponse.from(planService.get(planId)));
    }

    @PostMapping("/{planId}/confirm")
    @Operation(summary = "由当前用户显式确认待确认训练计划并激活")
    public BaseResponse<TrainingPlanResponse> confirm(
            @PathVariable UUID planId,
            @Valid @RequestBody TrainingPlanVersionRequest request
    ) {
        return ResultUtils.success(
                TrainingPlanResponse.from(planService.activate(planId, request.expectedVersion()))
        );
    }

    @PostMapping("/{planId}/items/{itemId}/start")
    @Operation(summary = "由当前用户显式开始活动计划中的任务")
    public BaseResponse<TrainingPlanResponse> startItem(
            @PathVariable UUID planId,
            @PathVariable UUID itemId,
            @Valid @RequestBody TrainingPlanVersionRequest request
    ) {
        return ResultUtils.success(
                TrainingPlanResponse.from(planService.startItem(planId, request.expectedVersion(), itemId))
        );
    }

    @PostMapping("/{planId}/items/{itemId}/complete")
    @Operation(summary = "由当前用户提交证据并完成活动计划中的任务")
    public BaseResponse<TrainingPlanResponse> completeItem(
            @PathVariable UUID planId,
            @PathVariable UUID itemId,
            @Valid @RequestBody CompleteTrainingPlanItemRequest request
    ) {
        return ResultUtils.success(
                TrainingPlanResponse.from(
                        planService.completeItem(planId, request.expectedVersion(), itemId, request.evidenceRefs())
                )
        );
    }

    @PostMapping("/{planId}/complete")
    @Operation(summary = "全部任务完成后由当前用户显式完成训练计划")
    public BaseResponse<TrainingPlanResponse> complete(
            @PathVariable UUID planId,
            @Valid @RequestBody TrainingPlanVersionRequest request
    ) {
        return ResultUtils.success(
                TrainingPlanResponse.from(planService.complete(planId, request.expectedVersion()))
        );
    }

    @PostMapping("/{planId}/cancel")
    @Operation(summary = "由当前用户取消待确认或执行中的训练计划")
    public BaseResponse<TrainingPlanResponse> cancel(
            @PathVariable UUID planId,
            @Valid @RequestBody TrainingPlanVersionRequest request
    ) {
        return ResultUtils.success(
                TrainingPlanResponse.from(planService.cancel(planId, request.expectedVersion()))
        );
    }

    @GetMapping
    @Operation(summary = "分页查询当前用户的训练计划")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "返回训练计划历史"),
            @ApiResponse(responseCode = "400", description = "status、limit或cursor不合法")
    })
    public BaseResponse<TrainingPlanPageResponse> list(
            @RequestParam(required = false) TrainingPlan.PlanStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResultUtils.success(TrainingPlanPageResponse.from(
                planService.list(status, cursor, limit)
        ));
    }

    /**
     * @program: CareerForge-AI
     * @description: 训练计划分页响应
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页计划
     * @param nextCursor 下一页Cursor
     * @param hasMore 是否存在下一页
     */
    public record TrainingPlanPageResponse(
            List<TrainingPlanResponse> items,
            String nextCursor,
            boolean hasMore
    ) {
        static TrainingPlanPageResponse from(TrainingPlanApplicationService.PlanPage page) {
            return new TrainingPlanPageResponse(
                    page.items().stream().map(TrainingPlanResponse::from).toList(),
                    page.nextCursor(),
                    page.hasMore()
            );
        }
    }
}