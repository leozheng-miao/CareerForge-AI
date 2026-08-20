package com.leo.careerforgeai.agent.api;

import com.leo.careerforgeai.agent.api.dto.CoachingRunResponse;
import com.leo.careerforgeai.agent.api.dto.CreateCoachingRunRequest;
import com.leo.careerforgeai.agent.application.run.CoachingRunApplicationService;
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
 * @description: 提供Coaching Run同步提交和owner隔离查询API
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@RestController
@RequestMapping("/api/coaching-runs")
@RequiredArgsConstructor
@Tag(name = "Coaching Run")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunController {

    private final CoachingRunApplicationService applicationService;

    @PostMapping
    @Operation(summary = "同步提交Coaching Run")
    public BaseResponse<CoachingRunResponse> submit(
            @Valid @RequestBody CreateCoachingRunRequest request
    ) {
        return ResultUtils.success(
                CoachingRunResponse.from(
                        applicationService.submit(
                                request.sessionId(),
                                request.requestId(),
                                request.expectedSessionVersion(),
                                request.message()
                        )
                )
        );
    }

    @GetMapping("/{runId}")
    @Operation(summary = "查询当前用户的Coaching Run")
    public BaseResponse<CoachingRunResponse> get(@PathVariable UUID runId) {
        return ResultUtils.success(CoachingRunResponse.from(applicationService.get(runId)));
    }
}