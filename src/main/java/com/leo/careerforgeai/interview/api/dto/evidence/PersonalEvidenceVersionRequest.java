package com.leo.careerforgeai.interview.api.dto.evidence;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @program: CareerForge-AI
 * @description: 定义个人证据生命周期操作需要提交的版本前置条件
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param expectedVersion 用户操作前读取到的当前ACTIVE版本
 **/
public record PersonalEvidenceVersionRequest(
        @NotNull @Min(1) Long expectedVersion
) {
}