package com.leo.careerforgeai.interview.api.controller;

import com.leo.careerforgeai.interview.api.dto.session.CreateMockInterviewRequest;
import com.leo.careerforgeai.interview.api.dto.session.MockInterviewSessionResponse;
import com.leo.careerforgeai.interview.api.dto.session.StartMockInterviewRequest;
import com.leo.careerforgeai.interview.application.execution.MockInterviewAsyncSubmissionApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewCreationApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户模拟面试的创建、异步启动和状态查询API
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@RestController
@RequestMapping("/api/mock-interviews")
@Tag(name = "Mock Interview")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MockInterviewController {

    private final MockInterviewCreationApplicationService creationService;
    private final MockInterviewAsyncSubmissionApplicationService asyncSubmissionService;
    private final MockInterviewLifecycleApplicationService lifecycleService;

    public MockInterviewController(MockInterviewCreationApplicationService creationService,
                                   MockInterviewAsyncSubmissionApplicationService asyncSubmissionService,
                                   MockInterviewLifecycleApplicationService lifecycleService) {
        this.creationService = Objects.requireNonNull(creationService, "creationService不能为空");
        this.asyncSubmissionService = Objects.requireNonNull(asyncSubmissionService, "asyncSubmissionService不能为空");
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

    @GetMapping("/{interviewId}")
    @Operation(summary = "查询当前用户模拟面试的MySQL事实状态")
    public BaseResponse<MockInterviewSessionResponse> get(@PathVariable UUID interviewId) {
        return ResultUtils.success(MockInterviewSessionResponse.from(lifecycleService.get(interviewId)));
    }
}