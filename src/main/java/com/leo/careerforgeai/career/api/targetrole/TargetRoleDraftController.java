package com.leo.careerforgeai.career.api.targetrole;

import com.leo.careerforgeai.career.api.dto.targetrole.ConfirmTargetRoleDraftRequest;
import com.leo.careerforgeai.career.api.dto.targetrole.CreateTargetRoleDraftRequest;
import com.leo.careerforgeai.career.api.dto.targetrole.TargetRoleDraftResponse;
import com.leo.careerforgeai.career.api.dto.targetrole.TargetRoleResponse;
import com.leo.careerforgeai.career.application.targetrole.TargetRoleApplicationService;
import com.leo.careerforgeai.career.application.targetrole.TargetRoleDraftApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供目标岗位草案创建和当前owner受控查询API
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@RestController
@RequestMapping("/api/target-role-drafts")
@RequiredArgsConstructor
@Tag(name = "Target Role Draft")
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class TargetRoleDraftController {

    private final TargetRoleDraftApplicationService applicationService;

    private final TargetRoleApplicationService targetRoleService;

    @PostMapping
    @Operation(summary = "解析JD并创建PENDING目标岗位草案")
    public BaseResponse<TargetRoleDraftResponse> create(
            @Valid @RequestBody CreateTargetRoleDraftRequest request
    ) {
        return ResultUtils.success(
                TargetRoleDraftResponse.from(
                        applicationService.createDraft(
                                request.sourceRef(),
                                request.jdText()
                        )
                )
        );
    }

    @GetMapping("/{draftId}")
    @Operation(summary = "查询当前用户的目标岗位草案")
    public BaseResponse<TargetRoleDraftResponse> get(
            @PathVariable UUID draftId
    ) {
        return ResultUtils.success(
                TargetRoleDraftResponse.from(
                        applicationService.getDraft(draftId)
                )
        );
    }

    @PostMapping("/{draftId}/confirm")
    @Operation(summary = "确认PENDING草案并创建不可变TargetRole版本")
    public BaseResponse<TargetRoleResponse> confirm(
            @PathVariable UUID draftId,
            @Valid @RequestBody
            ConfirmTargetRoleDraftRequest request
    ) {
        return ResultUtils.success(
                TargetRoleResponse.from(targetRoleService.confirmDraft(draftId, request.expectedVersion())
                )
        );
    }
}