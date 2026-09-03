package com.leo.careerforgeai.career.api.skillgap;

import com.leo.careerforgeai.career.api.dto.skillgap.GenerateSkillGapSnapshotRequest;
import com.leo.careerforgeai.career.api.dto.skillgap.SkillGapSnapshotResponse;
import com.leo.careerforgeai.career.application.skillgap.SkillGapSnapshotApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户能力差距快照生成和owner受控查询API
 * @author: Miao Zheng
 * @date: 2026-08-17
 */
@RestController
@RequestMapping("/api/skill-gap-snapshots")
@RequiredArgsConstructor
@Tag(name = "Skill Gap Snapshot")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class SkillGapSnapshotController {
    private final SkillGapSnapshotApplicationService applicationService;

    @PostMapping
    @Operation(summary = "基于固定岗位和技能画像版本生成能力差距快照")
    public BaseResponse<SkillGapSnapshotResponse> generate(
            @Valid @RequestBody GenerateSkillGapSnapshotRequest request
    ) {
        return ResultUtils.success(SkillGapSnapshotResponse.from(
                applicationService.generate(
                        request.targetRoleId(),
                        request.expectedTargetRoleVersion(),
                        request.expectedProfileVersion()
                )
        ));
    }

    @GetMapping("/{snapshotId}")
    @Operation(summary = "查询当前用户指定的能力差距快照")
    public BaseResponse<SkillGapSnapshotResponse> get(@PathVariable UUID snapshotId) {
        return ResultUtils.success(
                SkillGapSnapshotResponse.from(applicationService.get(snapshotId))
        );
    }

    @GetMapping
    @Operation(summary = "分页查询当前用户的能力差距快照历史")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "返回快照历史"),
            @ApiResponse(responseCode = "400", description = "limit或cursor不合法")
    })
    public BaseResponse<SkillGapSnapshotPageResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResultUtils.success(SkillGapSnapshotPageResponse.from(
                applicationService.list(cursor, limit)
        ));
    }

    /**
     * @program: CareerForge-AI
     * @description: 能力差距快照分页响应
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页快照
     * @param nextCursor 下一页Cursor
     * @param hasMore 是否存在下一页
     */
    public record SkillGapSnapshotPageResponse(
            List<SkillGapSnapshotResponse> items,
            String nextCursor,
            boolean hasMore
    ) {
        static SkillGapSnapshotPageResponse from(
                SkillGapSnapshotApplicationService.SnapshotPage page
        ) {
            return new SkillGapSnapshotPageResponse(
                    page.items().stream().map(SkillGapSnapshotResponse::from).toList(),
                    page.nextCursor(),
                    page.hasMore()
            );
        }
    }
}