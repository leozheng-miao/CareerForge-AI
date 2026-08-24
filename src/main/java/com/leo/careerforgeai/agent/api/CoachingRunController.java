package com.leo.careerforgeai.agent.api;

import com.leo.careerforgeai.agent.api.dto.CoachingRunResponse;
import com.leo.careerforgeai.agent.api.dto.CreateCoachingRunRequest;
import com.leo.careerforgeai.agent.api.sse.CoachingRunSseService;
import com.leo.careerforgeai.agent.application.run.CoachingRunApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAsyncSubmissionApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供Coaching Run异步提交和owner隔离查询API
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@RestController
@RequestMapping("/api/coaching-runs")
@RequiredArgsConstructor
@Tag(name = "Coaching Run")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunController {

    private final CoachingRunAsyncSubmissionApplicationService submissionService;
    private final CoachingRunApplicationService applicationService;
    private final CoachingRunSseService sseService;

    @PostMapping
    @Operation(summary = "异步提交Coaching Run")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Run已经接受并提交异步执行"),
            @ApiResponse(responseCode = "409", description = "requestId冲突或Run版本冲突"),
            @ApiResponse(responseCode = "429", description = "owner请求限流或本地执行容量已满"),
            @ApiResponse(responseCode = "503", description = "Redis限流基础设施不可用或Run执行器正在关闭")
    })
    public ResponseEntity<BaseResponse<CoachingRunResponse>> submit(
            @Valid @RequestBody CreateCoachingRunRequest request
    ) {
        CoachingRunResponse response = CoachingRunResponse.from(
                submissionService.submit(
                        request.sessionId(),
                        request.requestId(),
                        request.expectedSessionVersion(),
                        request.message()
                )
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ResultUtils.success(response));
    }

    @GetMapping("/{runId}")
    @Operation(summary = "查询当前用户的Coaching Run")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "返回Run当前事实"),
            @ApiResponse(responseCode = "404", description = "Run不存在或不属于当前用户")
    })
    public BaseResponse<CoachingRunResponse> get(@PathVariable UUID runId) {
        return ResultUtils.success(CoachingRunResponse.from(applicationService.get(runId)));
    }

    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "观察Coaching Run安全事件并支持Last-Event-ID续读")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "建立SSE事件流"),
            @ApiResponse(responseCode = "400", description = "runId或Last-Event-ID格式不合法"),
            @ApiResponse(responseCode = "404", description = "Run不存在或不属于当前用户")
    })
    public SseEmitter events(
            @PathVariable UUID runId,
            @Parameter(description = "最后成功接收的Redis Stream事件ID，例如1724200000000-0")
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        return sseService.open(runId, lastEventId);
    }
}