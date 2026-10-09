package com.leo.careerforgeai.interviewflow.application.port;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 隔离快速面试准备流程与公共题库检索实现
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public interface InterviewQuestionKnowledgePort {

    List<KnowledgeQuestionCandidate> search(
            String direction,
            String objective,
            int limit
    );

    record KnowledgeQuestionCandidate(
            String content,
            List<SourceReference> sourceReferences
    ) {

        public KnowledgeQuestionCandidate {
            content = requireText(content, "content");
            Objects.requireNonNull(
                    sourceReferences,
                    "sourceReferences不能为空"
            );
            if (sourceReferences.isEmpty()) {
                throw new IllegalArgumentException(
                        "题库候选必须保留至少一个来源"
                );
            }
            if (sourceReferences.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "sourceReferences不能包含null"
                );
            }
            sourceReferences = List.copyOf(sourceReferences);
        }
    }

    record SourceReference(
            String knowledgeBaseId,
            String documentId,
            String chunkId,
            String sourceHash,
            Integer startOffset,
            Integer endOffset
    ) {

        public SourceReference {
            knowledgeBaseId = requireText(
                    knowledgeBaseId,
                    "knowledgeBaseId"
            );
            documentId = requireText(documentId, "documentId");
            chunkId = requireText(chunkId, "chunkId");
            boolean hasOffsets = sourceHash != null
                    || startOffset != null
                    || endOffset != null;
            if (hasOffsets) {
                if (sourceHash == null
                        || !sourceHash.matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException(
                            "sourceHash必须为小写SHA-256"
                    );
                }
                if (startOffset == null || startOffset < 0
                        || endOffset == null
                        || endOffset <= startOffset) {
                    throw new IllegalArgumentException("来源偏移不合法");
                }
            }
        }

        public SourceReference(
                String knowledgeBaseId,
                String documentId,
                String chunkId
        ) {
            this(
                    knowledgeBaseId,
                    documentId,
                    chunkId,
                    null,
                    null,
                    null
            );
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.strip();
    }
}
