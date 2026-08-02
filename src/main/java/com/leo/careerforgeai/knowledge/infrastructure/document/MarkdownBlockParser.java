package com.leo.careerforgeai.knowledge.infrastructure.document;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 在 Section 内识别段落、顶层列表项和围栏代码块，并保留精确 Offset
 * @author: Miao Zheng
 * @date: 2026-07-31 17:33
 **/
final class MarkdownBlockParser {

    List<MarkdownBlock> parse(MarkdownSection section) {
        if (section == null) throw new IllegalArgumentException("section 不能为空");

        String content = section.content();
        List<MarkdownBlock> blocks = new ArrayList<>();
        PendingBlock current = null;
        String fenceMarker = null;
        int lineStart = 0;

        while (lineStart <= content.length()) {
            int newlineIndex = content.indexOf('\n', lineStart);
            int lineEnd = newlineIndex >= 0 ? newlineIndex : content.length();
            String line = content.substring(lineStart, lineEnd);

            if (fenceMarker != null) {
                current.endOffset = lineEnd;
                if (isClosingFence(line, fenceMarker)) {
                    fenceMarker = null;
                    addBlock(blocks, section, current);
                    current = null;
                }
            } else if (line.isBlank()) {
                addBlock(blocks, section, current);
                current = null;
            } else {
                String openingFence = detectFenceMarker(line);
                if (openingFence != null) {
                    addBlock(blocks, section, current);
                    current = new PendingBlock(MarkdownBlockType.FENCED_CODE, lineStart, lineEnd, -1);
                    fenceMarker = openingFence;
                } else {
                    int listIndent = listItemIndent(line);
                    if (listIndent >= 0) {
                        if (current != null && current.type == MarkdownBlockType.LIST_ITEM && listIndent > current.listIndent) {
                            current.endOffset = lineEnd;
                        } else {
                            addBlock(blocks, section, current);
                            current = new PendingBlock(MarkdownBlockType.LIST_ITEM, lineStart, lineEnd, listIndent);
                        }
                    } else if (current == null) {
                        current = new PendingBlock(MarkdownBlockType.PARAGRAPH, lineStart, lineEnd, -1);
                    } else {
                        current.endOffset = lineEnd;
                    }
                }
            }

            if (newlineIndex < 0) break;
            lineStart = lineEnd + 1;
        }

        addBlock(blocks, section, current);
        return List.copyOf(blocks);
    }

    private int listItemIndent(String line) {
        int indentation = 0;
        while (indentation < line.length() && line.charAt(indentation) == ' ') indentation++;
        if (indentation > 3 || indentation >= line.length()) return -1;

        int index = indentation;
        char marker = line.charAt(index);
        if (marker == '-' || marker == '*' || marker == '+') return isMarkerBoundary(line, index + 1) ? indentation : -1;

        if (!Character.isDigit(marker)) return -1;
        int digitStart = index;
        while (index < line.length() && Character.isDigit(line.charAt(index)) && index - digitStart < 9) index++;
        if (index == digitStart || index >= line.length()) return -1;
        if (line.charAt(index) != '.' && line.charAt(index) != ')') return -1;
        return isMarkerBoundary(line, index + 1) ? indentation : -1;
    }

    private boolean isMarkerBoundary(String line, int index) {
        return index == line.length() || Character.isWhitespace(line.charAt(index));
    }

    private String detectFenceMarker(String line) {
        String candidate = line.stripLeading();
        if (candidate.isEmpty()) return null;

        char marker = candidate.charAt(0);
        if (marker != '`' && marker != '~') return null;

        int length = 0;
        while (length < candidate.length() && candidate.charAt(length) == marker) length++;
        return length >= 3 ? candidate.substring(0, length) : null;
    }

    private boolean isClosingFence(String line, String openingFence) {
        String candidate = line.stripLeading();
        char marker = openingFence.charAt(0);
        int length = 0;
        while (length < candidate.length() && candidate.charAt(length) == marker) length++;
        return length >= openingFence.length() && candidate.substring(length).isBlank();
    }

    private void addBlock(List<MarkdownBlock> blocks, MarkdownSection section, PendingBlock block) {
        if (block == null) return;

        int absoluteStart = section.startOffset() + block.startOffset;
        int absoluteEnd = section.startOffset() + block.endOffset;
        String blockContent = section.content().substring(block.startOffset, block.endOffset);
        blocks.add(new MarkdownBlock(block.type, absoluteStart, absoluteEnd, blockContent));
    }

    private static final class PendingBlock {
        private final MarkdownBlockType type;
        private final int startOffset;
        private final int listIndent;
        private int endOffset;

        private PendingBlock(MarkdownBlockType type, int startOffset, int endOffset, int listIndent) {
            this.type = type;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.listIndent = listIndent;
        }
    }
}