package com.leo.careerforgeai.interviewflow.api.answer;

import com.leo.careerforgeai.interviewflow.application.answer.InterviewAnswerApplicationService;
import com.leo.careerforgeai.interviewflow.application.answer.InterviewAnswerApplicationService.DraftResult;
import com.leo.careerforgeai.interviewflow.application.answer.InterviewAnswerApplicationService.SubmissionResult;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前题草稿、正式提交和跳过API
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@RestController
@RequestMapping(
        "/api/interviews/{interviewId}/questions/{questionId}"
)
@Tag(name = "Interview Flow Answer")
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class InterviewAnswerController {

    private final InterviewAnswerApplicationService service;

    public InterviewAnswerController(
            InterviewAnswerApplicationService service
    ) {
        this.service = Objects.requireNonNull(service, "service不能为null");
    }

    @PutMapping("/draft")
    @Operation(summary = "使用答案版本保存当前题草稿")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "草稿已保存"),
            @ApiResponse(responseCode = "400", description = "请求参数不合法"),
            @ApiResponse(responseCode = "404", description = "面试或题目不存在"),
            @ApiResponse(responseCode = "409", description = "草稿版本或题目状态冲突")
    })
    public BaseResponse<DraftResult> saveDraft(
            @PathVariable UUID interviewId,
            @PathVariable UUID questionId,
            @Valid @RequestBody SaveDraftRequest request
    ) {
        return ResultUtils.success(service.saveDraft(
                request.toCommand(interviewId, questionId)
        ));
    }

    @PostMapping("/submit")
    @Operation(summary = "幂等冻结当前题回答并转入后台处理")
    public BaseResponse<SubmissionResult> submit(
            @PathVariable UUID interviewId,
            @PathVariable UUID questionId,
            @Valid @RequestBody SubmitAnswerRequest request
    ) {
        return ResultUtils.success(service.submit(
                request.toCommand(interviewId, questionId)
        ));
    }

    @PostMapping("/skip")
    @Operation(summary = "幂等跳过当前题并持久化跳过事实")
    public BaseResponse<SubmissionResult> skip(
            @PathVariable UUID interviewId,
            @PathVariable UUID questionId,
            @Valid @RequestBody SkipQuestionRequest request
    ) {
        return ResultUtils.success(service.skip(
                request.toCommand(interviewId, questionId)
        ));
    }
}
