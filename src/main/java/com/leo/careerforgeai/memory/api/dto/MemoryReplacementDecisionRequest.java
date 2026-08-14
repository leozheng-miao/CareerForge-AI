package com.leo.careerforgeai.memory.api.dto;

import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义用户显式确认Memory替代时允许提交的新Memory及新旧版本
 * @author: Miao Zheng
 * @date: 2026-08-14
 * @param replacementMemoryId 准备替代旧值的PENDING Memory ID
 * @param expectedExistingVersion 用户看到的旧CONFIRMED Memory版本
 * @param expectedReplacementVersion 用户看到的新PENDING Memory版本
 * @param note 用户提供的可选替代说明
 **/
public record MemoryReplacementDecisionRequest(
        @NotNull
        UUID replacementMemoryId,

        @NotNull
        @PositiveOrZero
        Long expectedExistingVersion,

        @NotNull
        @PositiveOrZero
        Long expectedReplacementVersion,

        @Size(max = MemoryDecision.MAX_NOTE_LENGTH)
        String note
) {
}