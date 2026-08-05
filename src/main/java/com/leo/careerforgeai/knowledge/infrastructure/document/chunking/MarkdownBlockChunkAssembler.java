package com.leo.careerforgeai.knowledge.infrastructure.document.chunking;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 按照原文顺序合并相邻 Block，在不超过最大字符数的前提下生成连续 Chunk 草稿
 * @author: Miao Zheng
 * @date: 2026-07-31
 **/
final class MarkdownBlockChunkAssembler {

    List<ChunkDraft> assemble(MarkdownSection section, List<MarkdownBlock> blocks, int maxChunkChars) {
        return assemble(section, blocks, maxChunkChars, 0);
    }

    List<ChunkDraft> assemble(MarkdownSection section, List<MarkdownBlock> blocks, int maxChunkChars, int overlapChars) {
        if (section == null) throw new IllegalArgumentException("section 不能为空");
        if (blocks == null) throw new IllegalArgumentException("blocks 不能为空");
        if (maxChunkChars <= 0) throw new IllegalArgumentException("maxChunkChars 必须大于 0");
        if (overlapChars < 0) throw new IllegalArgumentException("overlapChars 不能小于 0");
        if (overlapChars >= maxChunkChars) throw new IllegalArgumentException("overlapChars 必须小于 maxChunkChars");
        if (blocks.isEmpty()) return List.of();

        validateBlocks(section, blocks, maxChunkChars);

        List<ChunkDraft> drafts = new ArrayList<>();
        int startIndex = 0;

        while (startIndex < blocks.size()) {
            int endIndex = startIndex;
            int chunkStart = blocks.get(startIndex).startOffset();

            while (endIndex + 1 < blocks.size() && blocks.get(endIndex + 1).endOffset() - chunkStart <= maxChunkChars) endIndex++;

            addDraft(drafts, section, chunkStart, blocks.get(endIndex).endOffset());
            if (endIndex == blocks.size() - 1) break;

            startIndex = findNextStartIndex(blocks, startIndex, endIndex, endIndex + 1, maxChunkChars, overlapChars);
        }

        return List.copyOf(drafts);
    }

    private int findNextStartIndex(List<MarkdownBlock> blocks, int currentStartIndex, int currentEndIndex, int nextNewBlockIndex, int maxChunkChars, int overlapChars) {
        int nextStartIndex = nextNewBlockIndex;
        if (overlapChars == 0) return nextStartIndex;

        int previousChunkEnd = blocks.get(currentEndIndex).endOffset();
        int nextNewBlockEnd = blocks.get(nextNewBlockIndex).endOffset();

        for (int candidate = currentEndIndex; candidate > currentStartIndex; candidate--) {
            int candidateStart = blocks.get(candidate).startOffset();
            int overlapLength = previousChunkEnd - candidateStart;
            int nextMinimumChunkLength = nextNewBlockEnd - candidateStart;

            if (overlapLength <= overlapChars && nextMinimumChunkLength <= maxChunkChars) nextStartIndex = candidate;
        }

        return nextStartIndex;
    }

    private void validateBlocks(MarkdownSection section, List<MarkdownBlock> blocks, int maxChunkChars) {
        int previousEnd = section.startOffset();

        for (MarkdownBlock block : blocks) {
            validateBlock(section, block, previousEnd, maxChunkChars);
            previousEnd = block.endOffset();
        }
    }

    private void validateBlock(MarkdownSection section, MarkdownBlock block, int previousEnd, int maxChunkChars) {
        if (block == null) throw new IllegalArgumentException("blocks 不能包含 null");
        if (block.startOffset() < section.startOffset() || block.endOffset() > section.endOffset()) throw new IllegalArgumentException("Block Offset 越过 Section");
        if (block.startOffset() < previousEnd) throw new IllegalArgumentException("Block 必须按 Offset 升序且不能重叠");
        if (block.endOffset() - block.startOffset() > maxChunkChars) {
            throw new IllegalArgumentException("Block 长度 " + (block.endOffset() - block.startOffset()) + " 超过 maxChunkChars " + maxChunkChars + "，必须先执行超长 Block 切分");
        }

        int relativeStart = block.startOffset() - section.startOffset();
        int relativeEnd = block.endOffset() - section.startOffset();
        if (!section.content().substring(relativeStart, relativeEnd).equals(block.content())) throw new IllegalArgumentException("Block 内容与 Section Offset 不一致");
    }

    private void addDraft(List<ChunkDraft> drafts, MarkdownSection section, int startOffset, int endOffset) {
        int relativeStart = startOffset - section.startOffset();
        int relativeEnd = endOffset - section.startOffset();
        String content = section.content().substring(relativeStart, relativeEnd);
        drafts.add(new ChunkDraft(section.sectionPath(), startOffset, endOffset, content));
    }
}