package com.leo.careerforgeai.knowledge.domain.retrieval;

import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;

import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 规定本次检索允许访问的知识库分区、文档类型和文档 ID
 * @author: Miao Zheng
 * @date: 2026-08-03 22:15
 **/
public record RetrievalScope(
        String knowledgeBaseId,
        Set<KnowledgeDocumentType> documentTypes,
        Set<String> documentIds
) {

    public RetrievalScope {
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) throw new IllegalArgumentException("knowledgeBaseId 不能为空");
        if (documentTypes != null && documentTypes.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("documentTypes 不能包含 null");
        if (documentIds != null && documentIds.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("documentIds 不能包含空值");
        documentTypes = documentTypes == null ? Set.of() : Set.copyOf(documentTypes);
        documentIds = documentIds == null ? Set.of() : Set.copyOf(documentIds);
    }
}