package com.leo.careerforgeai.interview.api.dto.evidence;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @program: CareerForge-AI
 * @description: 定义基于当前版本创建个人证据新版本的请求
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param expectedVersion 用户读取到的当前ACTIVE版本
 * @param sourceName 新版本材料名称
 * @param rawContent 新版本不可信文本或Markdown正文
 **/
public record UpdatePersonalEvidenceRequest(
        @NotNull @Min(1) Long expectedVersion,
        @NotBlank @Size(max = 255) String sourceName,
        @NotBlank @Size(max = 100000) String rawContent
) {
}