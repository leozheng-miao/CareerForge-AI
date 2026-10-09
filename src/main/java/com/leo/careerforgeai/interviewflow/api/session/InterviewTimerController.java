package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.application.session.InterviewTimerApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供面试活动心跳、主动暂停和确认恢复API
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@RestController
@RequestMapping("/api/interviews/{interviewId}")
@Tag(name = "Interview Flow Timer")
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class InterviewTimerController {

    private final InterviewTimerApplicationService service;

    public InterviewTimerController(InterviewTimerApplicationService service) {
        this.service = Objects.requireNonNull(service, "service不能为null");
    }

    @PostMapping("/heartbeat")
    @Operation(summary = "刷新当前面试的Redis页面活动租约")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "心跳已处理"),
            @ApiResponse(responseCode = "404", description = "面试不存在或不属于当前用户"),
            @ApiResponse(responseCode = "409", description = "当前面试不可计时")
    })
    public BaseResponse<InterviewHeartbeatResponse> heartbeat(
            @PathVariable UUID interviewId
    ) {
        return ResultUtils.success(InterviewHeartbeatResponse.from(
                service.heartbeat(interviewId)
        ));
    }

    @PostMapping("/pause")
    @Operation(summary = "结算当前有效作答片段并暂停面试计时")
    public BaseResponse<InterviewHeartbeatResponse> pause(
            @PathVariable UUID interviewId,
            @Valid @RequestBody TimerMutationRequest request
    ) {
        return ResultUtils.success(InterviewHeartbeatResponse.from(
                service.pause(interviewId, request.expectedVersion())
        ));
    }

    @PostMapping("/resume")
    @Operation(summary = "当完整题目已展示时确认恢复面试计时")
    public BaseResponse<InterviewHeartbeatResponse> resume(
            @PathVariable UUID interviewId,
            @Valid @RequestBody TimerMutationRequest request
    ) {
        return ResultUtils.success(InterviewHeartbeatResponse.from(
                service.resume(interviewId, request.expectedVersion())
        ));
    }

    public record TimerMutationRequest(
            @NotNull @PositiveOrZero Long expectedVersion
    ) {
    }
}
