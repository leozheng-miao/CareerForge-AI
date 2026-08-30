package com.leo.careerforgeai.interview.api.controller;

import com.leo.careerforgeai.interview.api.dto.answer.SubmitInterviewAnswerRequest;
import com.leo.careerforgeai.interview.api.dto.session.MockInterviewSessionResponse;
import com.leo.careerforgeai.interview.application.execution.MockInterviewAnswerAsyncSubmissionApplicationService;
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
 * @description: 提供当前用户模拟面试答案幂等提交和Graph异步恢复API
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@RestController
@RequestMapping("/api/mock-interviews")
@Tag(name = "Mock Interview Answer")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewAnswerController {

    private final MockInterviewAnswerAsyncSubmissionApplicationService submissionService;

    public InterviewAnswerController(MockInterviewAnswerAsyncSubmissionApplicationService submissionService) {
        this.submissionService = Objects.requireNonNull(submissionService, "submissionService不能为空");
    }

    @PostMapping("/{interviewId}/answers")
    @Operation(summary = "幂等保存当前问题答案并受控异步恢复面试Graph")
    public BaseResponse<MockInterviewSessionResponse> submit(
            @PathVariable UUID interviewId,
            @Valid @RequestBody SubmitInterviewAnswerRequest request
    ) {
        return ResultUtils.success(MockInterviewSessionResponse.from(
                submissionService.submit(
                        interviewId,
                        request.roundNo(),
                        request.questionId(),
                        request.requestId(),
                        request.expectedInterviewVersion(),
                        request.answerText()
                )
        ));
    }
}