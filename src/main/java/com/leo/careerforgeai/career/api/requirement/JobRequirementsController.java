package com.leo.careerforgeai.career.api.requirement;

import com.leo.careerforgeai.career.application.requirement.JobRequirementsParser;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-29 15:58
 **/
@RestController
@RequestMapping("/api/career/job-requirements")
@RequiredArgsConstructor
public class JobRequirementsController {
    private final JobRequirementsParser parser;

    @PostMapping("/parse")
    public BaseResponse<JobRequirements> parse(@Valid @RequestBody ParseRequest request) {
        return ResultUtils.success(parser.parse(request.jdText()));
    }
    public record ParseRequest(@NotBlank String jdText) {}

}