package com.leo.careerforgeai.knowledge.api.dto;

import com.leo.careerforgeai.knowledge.application.query.RagQueryResult;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswerStatus;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankStatus;

import java.util.List;

public record RagQueryResponse(
        String requestId,
        RagAnswerStatus status,
        String answer,
        List<RagCitationResponse> citations,
        RerankStatus rerankStatus,
        int retrievedCandidateCount,
        int contextChunkCount,
        long totalDurationMs
) {

    public RagQueryResponse {
        citations = List.copyOf(citations);
    }

    public static RagQueryResponse from(RagQueryResult result) {
        return new RagQueryResponse(
                result.requestId(),
                result.answer().status(),
                result.answer().answer(),
                result.answer().citations().stream().map(RagCitationResponse::from).toList(),
                result.rerankedResult().status(),
                result.retrievalResult().rrfChunks().size(),
                result.context().chunks().size(),
                result.totalDurationMs()
        );
    }
}