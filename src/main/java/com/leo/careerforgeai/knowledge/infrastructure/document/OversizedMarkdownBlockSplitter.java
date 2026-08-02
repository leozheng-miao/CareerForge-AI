package com.leo.careerforgeai.knowledge.infrastructure.document;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 将超长段落或列表项按句子、换行和安全字符边界拆成较小 Block；拒绝静默拆坏超长代码围栏
 * @author: Miao Zheng
 * @date: 2026-07-31 22:19
 **/
final class OversizedMarkdownBlockSplitter {

    List<MarkdownBlock> split(List<MarkdownBlock> blocks, int maxChunkChars) {
        if (blocks == null) throw new IllegalArgumentException("blocks 不能为空");
        if (maxChunkChars <= 0) throw new IllegalArgumentException("maxChunkChars 必须大于 0");

        List<MarkdownBlock> result = new ArrayList<>();

        for (MarkdownBlock block : blocks) {
            if (block == null) throw new IllegalArgumentException("blocks 不能包含 null");
            if (block.content().length() != block.endOffset() - block.startOffset()) throw new IllegalArgumentException("Block 内容与 Offset 长度不一致");

            if (block.content().length() <= maxChunkChars) {
                result.add(block);
            } else if (block.type() == MarkdownBlockType.FENCED_CODE) {
                throw new DocumentChunkingException("代码围栏长度 " + block.content().length() + " 超过 maxChunkChars " + maxChunkChars + "，首版策略拒绝静默拆分代码");
            } else {
                splitSemanticRanges(block, maxChunkChars, result);
            }
        }

        return List.copyOf(result);
    }

    private void splitSemanticRanges(MarkdownBlock block, int maxChunkChars, List<MarkdownBlock> result) {
        String content = block.content();
        int current = 0;

        while (current < content.length()) {
            current = skipWhitespace(content, current, content.length());
            if (current >= content.length()) break;

            int semanticEnd = findSemanticEnd(content, current);
            addRange(block, current, semanticEnd, maxChunkChars, result);
            current = semanticEnd;
        }
    }

    private int findSemanticEnd(String content, int startOffset) {
        for (int index = startOffset; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '\n') return index;
            if ("。！？；".indexOf(current) >= 0) return index + 1;
            if (".!?;".indexOf(current) >= 0 && isEnglishSentenceBoundary(content, index + 1)) return index + 1;
        }
        return content.length();
    }

    private boolean isEnglishSentenceBoundary(String content, int nextIndex) {
        return nextIndex == content.length() || Character.isWhitespace(content.charAt(nextIndex));
    }

    private void addRange(MarkdownBlock source, int rangeStart, int rangeEnd, int maxChunkChars, List<MarkdownBlock> result) {
        int current = rangeStart;

        while (rangeEnd - current > maxChunkChars) {
            int limit = current + maxChunkChars;
            int splitEnd = findWhitespaceBoundary(source.content(), current, limit);
            splitEnd = avoidBrokenSurrogatePair(source.content(), current, splitEnd);
            if (splitEnd <= current) throw new DocumentChunkingException("无法在 maxChunkChars 范围内安全切分文本");

            addFragment(source, current, splitEnd, result);
            current = skipWhitespace(source.content(), splitEnd, rangeEnd);
        }

        addFragment(source, current, rangeEnd, result);
    }

    private int findWhitespaceBoundary(String content, int startOffset, int limit) {
        int minimum = startOffset + Math.max(1, (limit - startOffset) / 2);

        for (int index = limit - 1; index >= minimum; index--) {
            if (Character.isWhitespace(content.charAt(index))) return index;
        }

        return limit;
    }

    private int avoidBrokenSurrogatePair(String content, int startOffset, int endOffset) {
        if (endOffset < content.length() && endOffset > startOffset
                && Character.isHighSurrogate(content.charAt(endOffset - 1))
                && Character.isLowSurrogate(content.charAt(endOffset))) {
            return endOffset - 1;
        }
        return endOffset;
    }

    private int skipWhitespace(String content, int startOffset, int endOffset) {
        int current = startOffset;
        while (current < endOffset && Character.isWhitespace(content.charAt(current))) current++;
        return current;
    }

    private void addFragment(MarkdownBlock source, int relativeStart, int relativeEnd, List<MarkdownBlock> result) {
        while (relativeEnd > relativeStart && Character.isWhitespace(source.content().charAt(relativeEnd - 1))) relativeEnd--;
        if (relativeStart >= relativeEnd) return;

        int absoluteStart = source.startOffset() + relativeStart;
        int absoluteEnd = source.startOffset() + relativeEnd;
        result.add(new MarkdownBlock(source.type(), absoluteStart, absoluteEnd, source.content().substring(relativeStart, relativeEnd)));
    }
}