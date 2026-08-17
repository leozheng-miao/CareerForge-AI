package com.leo.careerforgeai.career.api.targetrole;

import com.leo.careerforgeai.career.api.dto.targetrole.TargetRoleResponse;
import com.leo.careerforgeai.career.application.targetrole.TargetRoleApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户不可变目标岗位版本的受控查询API
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@RestController
@RequestMapping("/api/target-roles")
@RequiredArgsConstructor
@Tag(name = "Target Role")
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class TargetRoleController {

    private final TargetRoleApplicationService applicationService;

    @GetMapping("/latest")
    @Operation(summary = "查询当前用户最新确认的目标岗位")
    public BaseResponse<TargetRoleResponse> getLatest() {
        return ResultUtils.success(
                TargetRoleResponse.from(
                        applicationService.getLatest()
                )
        );
    }

    @GetMapping("/{targetRoleId}")
    @Operation(summary = "查询当前用户指定的目标岗位版本")
    public BaseResponse<TargetRoleResponse> get(
            @PathVariable UUID targetRoleId
    ) {
        return ResultUtils.success(
                TargetRoleResponse.from(
                        applicationService.get(targetRoleId)
                )
        );
    }
}