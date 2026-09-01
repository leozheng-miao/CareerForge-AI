package com.leo.careerforgeai.interview.api.dto.evidence;

import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @program: CareerForge-AI
 * @description: 定义创建文本或Markdown个人证据的请求
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param type 证据类型
 * @param sourceName 用户可识别的材料名称
 * @param rawContent 不可信文本或Markdown正文
 **/
public record CreatePersonalEvidenceRequest(
        @NotNull PersonalEvidenceType type,
        @NotBlank @Size(max = 255) String sourceName,
        @NotBlank @Size(max = 100000) String rawContent
) {
}