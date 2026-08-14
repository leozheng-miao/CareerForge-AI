package com.leo.careerforgeai.memory.api;

import com.leo.careerforgeai.memory.api.dto.MemoryCandidateResponse;
import com.leo.careerforgeai.memory.application.profile.MemoryProfileQueryApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户待确认Memory候选和后续有效长期画像的只读API
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
@Tag(name = "Memory Profile")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MemoryProfileController {

    private final MemoryProfileQueryApplicationService queryService;

    /** 返回当前用户全部待确认候选，不允许客户端控制owner或status。 */
    @GetMapping("/pending")
    @Operation(summary = "查询当前用户待确认的Memory候选")
    public BaseResponse<List<MemoryCandidateResponse>> findPendingCandidates() {
        return ResultUtils.success(queryService.findPendingCandidates()
                .stream()
                .map(MemoryCandidateResponse::from)
                .toList());
    }

    /** 返回当前用户全部生效的长期Memory。 */
    @GetMapping("/confirmed")
    @Operation(summary = "查询当前用户有效长期Memory")
    public BaseResponse<List<MemoryCandidateResponse>> findConfirmedProfile() {
        return ResultUtils.success(queryService.findConfirmedProfile()
                .stream()
                .map(MemoryCandidateResponse::from)
                .toList());
    }
}