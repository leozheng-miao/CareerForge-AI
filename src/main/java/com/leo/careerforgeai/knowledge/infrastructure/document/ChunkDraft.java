package com.leo.careerforgeai.knowledge.infrastructure.document;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 表示已经满足长度限制、但还没有生成稳定 ID 和完整领域元数据的 Chunk 内容
 * @author: Miao Zheng
 * @date: 2026-07-31
 **/
record ChunkDraft(
        List<String> sectionPath,
        int startOffset,
        int endOffset,
        String content
) {

    ChunkDraft {
        if (sectionPath == null) throw new IllegalArgumentException("sectionPath 不能为空");
        sectionPath = List.copyOf(sectionPath);
        if (sectionPath.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("sectionPath 不能包含空标题");
        if (startOffset < 0) throw new IllegalArgumentException("startOffset 不能小于 0");
        if (endOffset <= startOffset) throw new IllegalArgumentException("endOffset 必须大于 startOffset");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
        if (content.length() != endOffset - startOffset) throw new IllegalArgumentException("content 长度必须与 Offset 范围一致");
    }
}