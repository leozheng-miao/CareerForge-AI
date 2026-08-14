package com.leo.careerforgeai.memory.application.extraction.dto;

import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义模型提出的单条Memory候选，所有字段仍是不可信输出并需经过Java业务校验
 * @author: Miao Zheng
 * @date: 2026-08-13
 * @param type 模型建议的Memory业务类型，只允许首版四种类型
 * @param keyHint 用于生成normalizedKey的未信任提示值，不能直接作为最终标准键
 * @param content 模型提取的候选正文，不能直接进入长期画像
 * @param sourceTurnId 模型指定的主要来源Turn，后续必须通过当前owner和输入白名单校验
 * @param evidenceTurnIds 模型引用的证据Turn列表，后续必须执行归属、状态、重复和白名单校验
 * @param confidence 模型自评置信度，只能用于诊断或排序，不能代替用户确认
 **/
public record MemoryCandidateModelOutput(
        @NotNull
        MemoryType type,

        @NotBlank
        @Size(max = 128)
        String keyHint,

        @NotBlank
        @Size(max = MemoryItem.MAX_CONTENT_LENGTH)
        String content,

        @NotNull
        UUID sourceTurnId,

        @NotNull
        @Size(min = 1, max = MemoryItem.MAX_EVIDENCE_REFS)
        List<@NotNull UUID> evidenceTurnIds,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        BigDecimal confidence
) {
}