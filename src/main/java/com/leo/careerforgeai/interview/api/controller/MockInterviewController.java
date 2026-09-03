package com.leo.careerforgeai.interview.api.controller;

import com.leo.careerforgeai.interview.api.dto.session.CancelMockInterviewRequest;
import com.leo.careerforgeai.interview.api.dto.session.CreateMockInterviewRequest;
import com.leo.careerforgeai.interview.api.dto.session.MockInterviewSessionResponse;
import com.leo.careerforgeai.interview.api.dto.session.RetryInterruptedMockInterviewRequest;
import com.leo.careerforgeai.interview.api.dto.session.StartMockInterviewRequest;
import com.leo.careerforgeai.interview.application.execution.MockInterviewAsyncSubmissionApplicationService;
import com.leo.careerforgeai.interview.application.execution.MockInterviewReportRetryApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewCreationApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户模拟面试的创建、异步执行、报告恢复和状态查询API
 * @author: Miao Zheng
 * @date: 2026-08-31
 */
@RestController
@RequestMapping("/api/mock-interviews")
@Tag(name = "Mock Interview")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MockInterviewController {

    private final MockInterviewCreationApplicationService creationService;
    private final MockInterviewAsyncSubmissionApplicationService asyncSubmissionService;
    private final MockInterviewReportRetryApplicationService reportRetryService;
    private final MockInterviewLifecycleApplicationService lifecycleService;

    public MockInterviewController(
            MockInterviewCreationApplicationService creationService,
            MockInterviewAsyncSubmissionApplicationService asyncSubmissionService,
            MockInterviewReportRetryApplicationService reportRetryService,
            MockInterviewLifecycleApplicationService lifecycleService
    ) {
        this.creationService = Objects.requireNonNull(creationService, "creationService不能为空");
        this.asyncSubmissionService = Objects.requireNonNull(asyncSubmissionService, "asyncSubmissionService不能为空");
        this.reportRetryService = Objects.requireNonNull(reportRetryService, "reportRetryService不能为空");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService不能为空");
    }

    @PostMapping
    @Operation(summary = "冻结当前用户选择的输入版本并幂等创建模拟面试")
    public BaseResponse<MockInterviewSessionResponse> create(
            @Valid @RequestBody CreateMockInterviewRequest request
    ) {
        return ResultUtils.success(MockInterviewSessionResponse.from(
                creationService.create(request.requestId(), request.mode(), request.toSelection())
        ));
    }

    @PostMapping("/{interviewId}/start")
    @Operation(summary = "受控异步启动模拟面试并返回已接受状态")
    public BaseResponse<MockInterviewSessionResponse> start(
            @PathVariable UUID interviewId,
            @Valid @RequestBody StartMockInterviewRequest request
    ) {
        return ResultUtils.success(MockInterviewSessionResponse.from(
                asyncSubmissionService.submitStart(interviewId, request.expectedVersion())
        ));
    }

    @PostMapping("/{interviewId}/report/retry")
    @Operation(summary = "从安全Checkpoint重新执行当前用户中断的报告生成节点")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "恢复请求已接受或返回已经恢复的幂等结果"),
            @ApiResponse(responseCode = "400", description = "请求参数不合法"),
            @ApiResponse(responseCode = "404", description = "模拟面试不存在或不属于当前用户"),
            @ApiResponse(responseCode = "409", description = "版本或面试状态冲突"),
            @ApiResponse(responseCode = "429", description = "当前异步执行容量已满")
    })
    public BaseResponse<MockInterviewSessionResponse> retryReport(
            @PathVariable UUID interviewId,
            @Valid @RequestBody RetryInterruptedMockInterviewRequest request
    ) {
        return ResultUtils.success(MockInterviewSessionResponse.from(
                reportRetryService.submit(interviewId, request.expectedVersion())
        ));
    }

    @GetMapping("/{interviewId}")
    @Operation(summary = "查询当前用户模拟面试的MySQL事实状态")
    public BaseResponse<MockInterviewSessionResponse> get(@PathVariable UUID interviewId) {
        return ResultUtils.success(MockInterviewSessionResponse.from(lifecycleService.get(interviewId)));
    }

    @PostMapping("/{interviewId}/cancel")
    @Operation(summary = "使用乐观锁取消当前用户的非终态模拟面试")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "取消成功或返回已经取消的幂等结果"),
            @ApiResponse(responseCode = "400", description = "请求参数不合法"),
            @ApiResponse(responseCode = "404", description = "模拟面试不存在或不属于当前用户"),
            @ApiResponse(responseCode = "409", description = "版本冲突或面试已进入其他终态")
    })
    public BaseResponse<MockInterviewSessionResponse> cancel(
            @PathVariable UUID interviewId,
            @Valid @RequestBody CancelMockInterviewRequest request
    ) {
        return ResultUtils.success(MockInterviewSessionResponse.from(
                lifecycleService.cancel(interviewId, request.expectedVersion())
        ));
    }

    @GetMapping
    @Operation(summary = "分页查询当前用户的模拟面试历史")
    public BaseResponse<MockInterviewPageResponse> list(
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResultUtils.success(MockInterviewPageResponse.from(
                lifecycleService.list(status, cursor, limit)
        ));
    }

    /**
     * @program: CareerForge-AI
     * @description: 模拟面试历史分页响应
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页面试
     * @param nextCursor 下一页Cursor
     * @param hasMore 是否存在下一页
     */
    public record MockInterviewPageResponse(
            List<MockInterviewSessionResponse> items,
            String nextCursor,
            boolean hasMore
    ) {
        static MockInterviewPageResponse from(MockInterviewLifecycleApplicationService.SessionPage page) {
            return new MockInterviewPageResponse(
                    page.items().stream().map(MockInterviewSessionResponse::from).toList(),
                    page.nextCursor(),
                    page.hasMore()
            );
        }
    }
}