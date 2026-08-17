package com.leo.careerforgeai.career.api.skillgap;

import com.leo.careerforgeai.career.api.dto.skillgap.GenerateSkillGapSnapshotRequest;
import com.leo.careerforgeai.career.api.dto.skillgap.SkillGapSnapshotResponse;
import com.leo.careerforgeai.career.application.skillgap.SkillGapSnapshotApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

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
}