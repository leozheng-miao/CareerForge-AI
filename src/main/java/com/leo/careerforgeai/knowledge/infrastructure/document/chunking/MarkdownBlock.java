package com.leo.careerforgeai.knowledge.infrastructure.document.chunking;

/**
 * 保存一个段落、列表项或围栏代码块及其在清洗后文档中的绝对位置。
 * @param type
 * @param startOffset
 * @param endOffset
 * @param content
 */
record MarkdownBlock(
        MarkdownBlockType type,
        int startOffset,
        int endOffset,
        String content
) {

    MarkdownBlock {
        if (type == null) throw new IllegalArgumentException("type 不能为空");
        if (startOffset < 0) throw new IllegalArgumentException("startOffset 不能小于 0");
        if (endOffset <= startOffset) throw new IllegalArgumentException("endOffset 必须大于 startOffset");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content 不能为空");
    }
}