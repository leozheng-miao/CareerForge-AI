package com.leo.careerforgeai.interview.api.controller;

import com.leo.careerforgeai.interview.api.dto.question.CurrentInterviewQuestionResponse;
import com.leo.careerforgeai.interview.application.question.CurrentInterviewQuestionQueryApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户模拟面试待回答问题的安全查询API
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@RestController
@RequestMapping("/api/mock-interviews")
@Tag(name = "Mock Interview Question")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewQuestionController {

    private final CurrentInterviewQuestionQueryApplicationService queryService;

    public InterviewQuestionController(CurrentInterviewQuestionQueryApplicationService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService不能为空");
    }

    @GetMapping("/{interviewId}/current-question")
    @Operation(summary = "查询当前用户模拟面试正在等待回答的问题")
    public BaseResponse<CurrentInterviewQuestionResponse> getCurrentQuestion(
            @PathVariable UUID interviewId
    ) {
        return ResultUtils.success(CurrentInterviewQuestionResponse.from(
                queryService.getCurrent(interviewId)
        ));
    }
}