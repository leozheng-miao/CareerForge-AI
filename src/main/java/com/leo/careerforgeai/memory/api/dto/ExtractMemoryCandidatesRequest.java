package com.leo.careerforgeai.memory.api.dto;

import com.leo.careerforgeai.memory.application.extraction.MemoryCandidateApplicationService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义用户显式发起Memory候选提取时允许提交的Turn ID白名单
 * @author: Miao Zheng
 * @date: 2026-08-13
 * @param turnIds 当前Session中由用户主动选择的已完成Turn ID
 **/
public record ExtractMemoryCandidatesRequest(
        @NotEmpty
        @Size(max = MemoryCandidateApplicationService.MAX_SELECTED_TURNS)
        List<@NotNull UUID> turnIds
) {
}