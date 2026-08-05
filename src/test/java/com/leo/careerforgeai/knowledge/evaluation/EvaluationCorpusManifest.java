package com.leo.careerforgeai.knowledge.evaluation;

import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;

import java.util.List;

public record EvaluationCorpusManifest(
        String schemaVersion,
        String knowledgeBaseId,
        String cleaningVersion,
        String chunkerVersion,
        List<DocumentSnapshot> documents
) {

    public EvaluationCorpusManifest {
        requireText(schemaVersion, "schemaVersion");
        requireText(knowledgeBaseId, "knowledgeBaseId");
        requireText(cleaningVersion, "cleaningVersion");
        requireText(chunkerVersion, "chunkerVersion");
        if (documents == null || documents.isEmpty()) throw new IllegalArgumentException("documents 不能为空");
        documents = List.copyOf(documents);
        if (documents.stream().map(DocumentSnapshot::documentId).distinct().count() != documents.size()) throw new IllegalArgumentException("documents 不能包含重复 documentId");
    }

    public record DocumentSnapshot(
            String documentId,
            String documentName,
            KnowledgeDocumentType documentType,
            String sourcePath,
            String sourceHash
    ) {

        public DocumentSnapshot {
            requireText(documentId, "documentId");
            requireText(documentName, "documentName");
            if (documentType == null) throw new IllegalArgumentException("documentType 不能为空");
            requireText(sourcePath, "sourcePath");
            if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sourceHash 必须是小写 SHA-256");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " 不能为空");
    }
}