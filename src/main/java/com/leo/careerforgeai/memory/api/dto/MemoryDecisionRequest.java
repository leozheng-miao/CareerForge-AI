package com.leo.careerforgeai.memory.api.dto;

import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * @program: CareerForge-AI
 * @description: 定义用户确认或拒绝Memory时允许提交的版本和可选说明
 * @author: Miao Zheng
 * @date: 2026-08-14
 * @param expectedVersion 用户作出决策时看到的Memory版本
 * @param note 用户提供的可选决策说明，不能改变状态机或权限
 **/
public record MemoryDecisionRequest(
        @NotNull
        @PositiveOrZero
        Long expectedVersion,

        @Size(max = MemoryDecision.MAX_NOTE_LENGTH)
        String note
) {
}