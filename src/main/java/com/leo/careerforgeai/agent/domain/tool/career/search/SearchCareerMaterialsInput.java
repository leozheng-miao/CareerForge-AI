package com.leo.careerforgeai.agent.domain.tool.career.search;

import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 定义模型搜索职业材料时允许提交的查询和受限文档类型。
 * @author: Miao Zheng
 * @date: 2026-08-06 20:20
 **/
public record SearchCareerMaterialsInput(
        @NotBlank
        @Size(max = 500)
        String query,

        @Size(max = 2)
        Set<@NotNull KnowledgeDocumentType> documentTypes
) {

    public SearchCareerMaterialsInput {
        documentTypes = documentTypes == null ? Set.of() : Set.copyOf(documentTypes);
    }
}