package com.leo.careerforgeai.career.application.requirement;

import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.model.domain.ModelUsage;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存结构化岗位要求及内部模型调用的Token和耗时。
 * @author: Miao Zheng
 * @date: 2026-08-06 23:00
 **/
public record JobRequirementsParseResult(JobRequirements requirements, ModelUsage modelUsage, long modelDurationMs) {

    public JobRequirementsParseResult {
        Objects.requireNonNull(requirements, "requirements 不能为空");
        Objects.requireNonNull(modelUsage, "modelUsage 不能为空");
        if (modelDurationMs < 0) throw new IllegalArgumentException("modelDurationMs 不能小于 0");
    }
}