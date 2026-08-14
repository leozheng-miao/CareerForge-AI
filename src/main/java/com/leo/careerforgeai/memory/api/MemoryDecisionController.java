package com.leo.careerforgeai.memory.api;

import com.leo.careerforgeai.memory.api.dto.MemoryCandidateResponse;
import com.leo.careerforgeai.memory.api.dto.MemoryDecisionRequest;
import com.leo.careerforgeai.memory.api.dto.MemoryReplacementDecisionRequest;
import com.leo.careerforgeai.memory.application.profile.MemoryDecisionApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户确认、拒绝、显式替代或撤销Memory的受控API
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
@Tag(name = "Memory Decision")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MemoryDecisionController {

    private final MemoryDecisionApplicationService decisionService;

    /** 用户确认PENDING候选，使其成为有效长期Memory。 */
    @PostMapping("/{memoryId}/confirm")
    @Operation(summary = "确认PENDING Memory候选")
    public BaseResponse<MemoryCandidateResponse> confirm(
            @PathVariable UUID memoryId,
            @Valid @RequestBody MemoryDecisionRequest request
    ) {
        return ResultUtils.success(MemoryCandidateResponse.from(
                decisionService.confirm(memoryId, request.expectedVersion(), request.note())
        ));
    }

    /** 用户拒绝PENDING候选，使其不能进入有效长期画像。 */
    @PostMapping("/{memoryId}/reject")
    @Operation(summary = "拒绝PENDING Memory候选")
    public BaseResponse<MemoryCandidateResponse> reject(
            @PathVariable UUID memoryId,
            @Valid @RequestBody MemoryDecisionRequest request
    ) {
        return ResultUtils.success(MemoryCandidateResponse.from(
                decisionService.reject(memoryId, request.expectedVersion(), request.note())
        ));
    }

    /** 用户明确批准新候选替代旧CONFIRMED Memory。 */
    @PostMapping("/{existingMemoryId}/replace")
    @Operation(summary = "确认新Memory并替代旧Memory")
    public BaseResponse<MemoryCandidateResponse> confirmReplacement(
            @PathVariable UUID existingMemoryId,
            @Valid @RequestBody MemoryReplacementDecisionRequest request
    ) {
        return ResultUtils.success(MemoryCandidateResponse.from(
                decisionService.confirmReplacement(
                        existingMemoryId,
                        request.expectedExistingVersion(),
                        request.replacementMemoryId(),
                        request.expectedReplacementVersion(),
                        request.note()
                )
        ));
    }

    /** 用户撤销已经确认的Memory，使其退出有效长期画像。 */
    @PostMapping("/{memoryId}/revoke")
    @Operation(summary = "撤销已确认的Memory")
    public BaseResponse<MemoryCandidateResponse> revoke(
            @PathVariable UUID memoryId,
            @Valid @RequestBody MemoryDecisionRequest request
    ) {
        return ResultUtils.success(MemoryCandidateResponse.from(
                decisionService.revoke(memoryId, request.expectedVersion(), request.note())
        ));
    }
}