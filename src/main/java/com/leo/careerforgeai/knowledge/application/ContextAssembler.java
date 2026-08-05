package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 按最终排名去除同源高重复 Chunk，并在字符预算内组装回答上下文
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
@Component
@Slf4j
public class ContextAssembler {

    public static final String VERSION = "context-assembler-v1|jaccard=0.85|overlap=0.50";
    private static final int MAX_CONTEXT_CHARS = 100_000;
    private static final double HIGH_SIMILARITY_THRESHOLD = 0.85D;
    private static final double OVERLAP_THRESHOLD = 0.50D;
    private static final double OVERLAP_SIMILARITY_THRESHOLD = 0.50D;
    private static final int SHINGLE_SIZE = 3;

    /** 按有效排名选择完整 Chunk，并返回预算和去重统计。 */
    public AssembledContext assemble(List<RrfRankedChunk> rankedCandidates, int maxContentChars) {
        validateInput(rankedCandidates, maxContentChars);

        List<DocumentChunk> selected = new ArrayList<>();
        int usedContentChars = 0;
        int duplicateSkippedCount = 0;
        int budgetSkippedCount = 0;

        for (RrfRankedChunk candidate : rankedCandidates) {
            DocumentChunk chunk = candidate.chunk();
            if (isDuplicate(chunk, selected)) {
                duplicateSkippedCount++;
                continue;
            }

            int chunkChars = chunk.retrievalText().length();
            if (chunkChars > maxContentChars - usedContentChars) {
                budgetSkippedCount++;
                continue;
            }

            selected.add(chunk);
            usedContentChars += chunkChars;
        }

        AssembledContext result = new AssembledContext(selected, usedContentChars, maxContentChars, duplicateSkippedCount, budgetSkippedCount, VERSION);
        log.info("上下文组装完成，candidates={}, selected={}, usedContentChars={}, maxContentChars={}, duplicateSkipped={}, budgetSkipped={}, version={}", rankedCandidates.size(), selected.size(), usedContentChars, maxContentChars, duplicateSkippedCount, budgetSkippedCount, VERSION);
        return result;
    }

    /** 判断候选是否与已经选择的同源 Chunk 高度重复。 */
    private boolean isDuplicate(DocumentChunk candidate, List<DocumentChunk> selected) {
        for (DocumentChunk existing : selected) {
            if (!sameSource(candidate, existing)) continue;

            String candidateText = normalize(candidate.content());
            String existingText = normalize(existing.content());
            if (!candidateText.isEmpty() && candidateText.equals(existingText)) return true;

            double similarity = jaccardSimilarity(candidateText, existingText);
            if (similarity >= HIGH_SIMILARITY_THRESHOLD) return true;

            boolean adjacent = Math.abs(candidate.chunkIndex() - existing.chunkIndex()) <= 1;
            if (adjacent && overlapRatio(candidate, existing) >= OVERLAP_THRESHOLD && similarity >= OVERLAP_SIMILARITY_THRESHOLD) return true;
        }
        return false;
    }

    /** 限制去重只发生在同一知识库、同一文档和同一原始版本中。 */
    private boolean sameSource(DocumentChunk left, DocumentChunk right) {
        return left.knowledgeBaseId().equals(right.knowledgeBaseId())
                && left.documentId().equals(right.documentId())
                && left.sourceHash().equals(right.sourceHash());
    }

    /** 计算两个 Chunk 在清洗后原文位置上的重叠比例。 */
    private double overlapRatio(DocumentChunk left, DocumentChunk right) {
        int overlap = Math.max(0, Math.min(left.endOffset(), right.endOffset()) - Math.max(left.startOffset(), right.startOffset()));
        int shorterLength = Math.min(left.endOffset() - left.startOffset(), right.endOffset() - right.startOffset());
        return shorterLength == 0 ? 0D : (double) overlap / shorterLength;
    }

    /** 使用字符三元组 Jaccard 估算中英文内容重复程度。 */
    private double jaccardSimilarity(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return 0D;

        Set<String> leftShingles = shingles(left);
        Set<String> rightShingles = shingles(right);
        int intersection = 0;
        for (String shingle : leftShingles) {
            if (rightShingles.contains(shingle)) intersection++;
        }

        int union = leftShingles.size() + rightShingles.size() - intersection;
        return union == 0 ? 0D : (double) intersection / union;
    }

    /** 将文本转换为字符三元组集合。 */
    private Set<String> shingles(String text) {
        int[] codePoints = text.codePoints().toArray();
        if (codePoints.length < SHINGLE_SIZE) return Set.of(text);

        Set<String> result = new HashSet<>();
        for (int index = 0; index <= codePoints.length - SHINGLE_SIZE; index++) {
            result.add(new String(codePoints, index, SHINGLE_SIZE));
        }
        return result;
    }

    /** 删除空白和标点并统一大小写，为重复比较生成稳定文本。 */
    private String normalize(String content) {
        StringBuilder normalized = new StringBuilder(content.length());
        content.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(codePoint -> normalized.appendCodePoint(Character.toLowerCase(codePoint)));
        return normalized.toString();
    }

    /** 校验候选集合、Chunk ID 唯一性和字符预算。 */
    private void validateInput(List<RrfRankedChunk> rankedCandidates, int maxContentChars) {
        if (rankedCandidates == null) throw new IllegalArgumentException("rankedCandidates 不能为空");
        if (maxContentChars <= 0 || maxContentChars > MAX_CONTEXT_CHARS) throw new IllegalArgumentException("maxContentChars 必须在 1 到 " + MAX_CONTEXT_CHARS + " 之间");

        Set<String> chunkIds = new HashSet<>();
        for (RrfRankedChunk candidate : rankedCandidates) {
            if (candidate == null) throw new IllegalArgumentException("rankedCandidates 不能包含 null");
            if (!chunkIds.add(candidate.chunk().chunkId())) throw new IllegalArgumentException("rankedCandidates 包含重复 Chunk ID=" + candidate.chunk().chunkId());
        }
    }
}