package com.leo.careerforgeai.memory.api;

import com.leo.careerforgeai.memory.api.dto.ExtractMemoryCandidatesRequest;
import com.leo.careerforgeai.memory.api.dto.MemoryCandidateResponse;
import com.leo.careerforgeai.memory.application.extraction.MemoryCandidateApplicationService;
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

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户从已完成会话Turn显式提取Memory候选的受控API
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
@RestController
@RequestMapping("/api/coaching-sessions/{sessionId}/memory-candidate-extractions")
@RequiredArgsConstructor
@Tag(name = "Memory Candidate")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MemoryCandidateController {

    private final MemoryCandidateApplicationService applicationService;

    /** 提取并返回只能为PENDING的新候选或同一来源与槽位的已有候选。 */
    @PostMapping
    @Operation(summary = "从当前用户选择的已完成Turn提取Memory候选")
    public BaseResponse<List<MemoryCandidateResponse>> extract(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ExtractMemoryCandidatesRequest request
    ) {
        List<MemoryCandidateResponse> candidates = applicationService
                .extract(sessionId, request.turnIds())
                .candidates()
                .stream()
                .map(MemoryCandidateResponse::from)
                .toList();

        return ResultUtils.success(candidates);
    }
}