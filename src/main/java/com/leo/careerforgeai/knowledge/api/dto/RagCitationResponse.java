package com.leo.careerforgeai.knowledge.api.dto;

import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.answer.RagCitation;

import java.util.List;

public record RagCitationResponse(
        String chunkId,
        String documentId,
        String documentName,
        KnowledgeDocumentType documentType,
        String sourceHash,
        int chunkIndex,
        List<String> sectionPath,
        int startOffset,
        int endOffset
) {

    public RagCitationResponse {
        sectionPath = List.copyOf(sectionPath);
    }

    public static RagCitationResponse from(RagCitation citation) {
        return new RagCitationResponse(
                citation.chunkId(),
                citation.documentId(),
                citation.documentName(),
                citation.documentType(),
                citation.sourceHash(),
                citation.chunkIndex(),
                citation.sectionPath(),
                citation.startOffset(),
                citation.endOffset()
        );
    }
}