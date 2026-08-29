package com.leo.careerforgeai.interview.api.session;

import com.leo.careerforgeai.interview.api.dto.session.CreateMockInterviewRequest;
import com.leo.careerforgeai.interview.api.dto.session.MockInterviewSessionResponse;
import com.leo.careerforgeai.interview.application.session.MockInterviewCreationApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户模拟面试的幂等创建API
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@RestController
@RequestMapping("/api/mock-interviews")
@Tag(name = "Mock Interview")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MockInterviewController {

    private final MockInterviewCreationApplicationService creationService;

    public MockInterviewController(MockInterviewCreationApplicationService creationService) {
        this.creationService = Objects.requireNonNull(creationService, "creationService不能为空");
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
}