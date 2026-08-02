package com.leo.careerforgeai.knowledge.infrastructure.document;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 表示一个标题路径下、能够精确映射到清洗后文档位置的正文区域
 * @author: Miao Zheng
 * @date: 2026-07-31 17:26
 **/
record MarkdownSection(
        List<String> sectionPath,
        int startOffset,
        int endOffset,
        String content
) {

    MarkdownSection {
        if (sectionPath == null) throw new IllegalArgumentException("sectionPath 不能为空");
        sectionPath = List.copyOf(sectionPath);
        if (sectionPath.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("sectionPath 不能包含空标题");
        if (startOffset < 0) throw new IllegalArgumentException("startOffset 不能小于 0");
        if (endOffset <= startOffset) throw new IllegalArgumentException("endOffset 必须大于 startOffset");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
    }
}