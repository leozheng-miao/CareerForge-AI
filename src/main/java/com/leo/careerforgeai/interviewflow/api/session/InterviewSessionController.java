package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.application.session.InterviewSessionApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @program: CareerForge-AI
 * @description: 提供快速面试创建、会话查询、恢复查询和正式进入API
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@RestController
@RequestMapping("/api/interviews")
@Tag(name = "Interview Flow Session")
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class InterviewSessionController {

    private final InterviewSessionApplicationService service;

    public InterviewSessionController(
            InterviewSessionApplicationService service
    ) {
        this.service = Objects.requireNonNull(service, "service不能为null");
    }

    @PostMapping("/quick")
    @Operation(summary = "幂等创建AI Agent开发方向的60分钟快速面试")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功或返回幂等结果"),
            @ApiResponse(responseCode = "400", description = "请求参数不合法"),
            @ApiResponse(responseCode = "409", description = "已有正式进行中的面试")
    })
    public BaseResponse<InterviewSessionResponse> createQuick(
            @Valid @RequestBody CreateQuickInterviewRequest request
    ) {
        return ResultUtils.success(InterviewSessionResponse.from(
                service.createQuick(request.toCommand())
        ));
    }

    @PostMapping("/comprehensive")
    @Operation(summary = "创建综合面试并开始个性化准备")
    public BaseResponse<InterviewSessionResponse> createComprehensive(
            @Valid @RequestBody CreateComprehensiveInterviewRequest request) {
        return ResultUtils.success(InterviewSessionResponse.from(
                service.createComprehensive(request.toCommand())));
    }

    @PutMapping("/{interviewId}/setup")
    @Operation(summary = "正式开始前修改综合面试设置并使旧准备失效")
    public BaseResponse<InterviewSessionResponse> updateSetup(
            @PathVariable UUID interviewId,
            @Valid @RequestBody UpdateInterviewSetupRequest request) {
        return ResultUtils.success(InterviewSessionResponse.from(
                service.updateSetup(request.toCommand(interviewId))));
    }

    @GetMapping("/resumable")
    @Operation(summary = "查询当前用户正式进行中的面试")
    public BaseResponse<InProgressInterviewResponse> resumable() {
        return ResultUtils.success(service.findInProgress()
                .map(InProgressInterviewResponse::from)
                .orElse(null));
    }

    @GetMapping("/{interviewId}")
    @Operation(summary = "查询当前用户的面试会话主状态")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "面试不存在或不属于当前用户")
    })
    public BaseResponse<InterviewSessionResponse> get(
            @PathVariable UUID interviewId
    ) {
        return ResultUtils.success(InterviewSessionResponse.from(
                service.get(interviewId)
        ));
    }

    @PostMapping("/{interviewId}/enter")
    @Operation(summary = "用乐观锁正式进入READY面试")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "进入成功或返回幂等结果"),
            @ApiResponse(responseCode = "404", description = "面试不存在或不属于当前用户"),
            @ApiResponse(responseCode = "409", description = "状态冲突或已有另一场进行中的面试")
    })
    public BaseResponse<InterviewSessionResponse> enter(
            @PathVariable UUID interviewId,
            @Valid @RequestBody EnterInterviewRequest request
    ) {
        return ResultUtils.success(InterviewSessionResponse.from(
                service.enter(
                        interviewId,
                        request.requestId(),
                        request.expectedVersion()
                )
        ));
    }

    @PostMapping("/{interviewId}/finish")
    @Operation(summary = "幂等结束面试并在有有效回答时启动报告任务")
    public BaseResponse<FinishInterviewResponse> finish(
            @PathVariable UUID interviewId,
            @Valid @RequestBody FinishInterviewRequest request
    ) {
        var result = service.finish(interviewId, request.requestId(), request.expectedVersion());
        return ResultUtils.success(new FinishInterviewResponse(
                InterviewSessionResponse.from(result.session()), result.reportGenerating()));
    }

    public record FinishInterviewRequest(
            @NotNull UUID requestId,
            @PositiveOrZero long expectedVersion
    ) {}

    public record FinishInterviewResponse(
            InterviewSessionResponse session,
            boolean reportGenerating
    ) {}
}
