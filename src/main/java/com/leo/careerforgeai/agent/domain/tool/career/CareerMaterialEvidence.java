package com.leo.careerforgeai.agent.domain.tool.career;

import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示允许返回给模型的有界职业材料证据和最小引用身份。
 * @author: Miao Zheng
 * @date: 2026-08-06 20:20
 **/
public record CareerMaterialEvidence(
        String chunkId,
        String documentId,
        String documentName,
        KnowledgeDocumentType documentType,
        List<String> sectionPath,
        String content
) {

    private static final int MAX_ID_CHARS = 200;
    private static final int MAX_DOCUMENT_NAME_CHARS = 255;
    private static final int MAX_SECTION_DEPTH = 12;
    private static final int MAX_SECTION_ITEM_CHARS = 200;
    private static final int MAX_CONTENT_CHARS = 1_200;

    public CareerMaterialEvidence {
        validateText(chunkId, "chunkId", MAX_ID_CHARS);
        validateText(documentId, "documentId", MAX_ID_CHARS);
        validateText(documentName, "documentName", MAX_DOCUMENT_NAME_CHARS);
        Objects.requireNonNull(documentType, "documentType 不能为空");

        if (sectionPath == null) throw new IllegalArgumentException("sectionPath 不能为空");
        if (sectionPath.size() > MAX_SECTION_DEPTH) throw new IllegalArgumentException("sectionPath 层级超过限制");
        for (String section : sectionPath) {
            validateText(section, "sectionPath 元素", MAX_SECTION_ITEM_CHARS);
        }
        sectionPath = List.copyOf(sectionPath);

        validateText(content, "content", MAX_CONTENT_CHARS);
    }

    /** 校验模型可见文本非空且不超过字段长度限制。 */
    private static void validateText(String value, String fieldName, int maxChars) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " 不能为空");
        if (value.length() > maxChars) throw new IllegalArgumentException(fieldName + " 超过长度限制");
    }
}