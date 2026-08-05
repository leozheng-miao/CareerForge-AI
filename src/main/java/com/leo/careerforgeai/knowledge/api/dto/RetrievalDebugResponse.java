package com.leo.careerforgeai.knowledge.api.dto;

import com.leo.careerforgeai.knowledge.application.retrieval.RetrievalDebugResult;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;

import java.util.List;

public record RetrievalDebugResponse(
        String requestId,
        String embeddingModel,
        int embeddingDimensions,
        long queryEmbeddingDurationMs,
        long bm25DurationMs,
        long vectorSearchDurationMs,
        long vectorTotalDurationMs,
        long fusionDurationMs,
        long totalDurationMs,
        List<RouteHitResponse> bm25Hits,
        List<RouteHitResponse> vectorHits,
        List<RrfHitResponse> rrfHits
) {

    public static RetrievalDebugResponse from(RetrievalDebugResult result) {
        RetrievalComparisonResult comparison = result.retrievalResult().comparisonResult();

        return new RetrievalDebugResponse(
                result.requestId(),
                comparison.embeddingModel(),
                comparison.embeddingDimensions(),
                comparison.queryEmbeddingDurationMs(),
                comparison.bm25Result().durationMs(),
                comparison.vectorResult().durationMs(),
                comparison.vectorTotalDurationMs(),
                result.retrievalResult().fusionDurationMs(),
                result.totalDurationMs(),
                comparison.bm25Result().chunks().stream().map(RouteHitResponse::from).toList(),
                comparison.vectorResult().chunks().stream().map(RouteHitResponse::from).toList(),
                result.retrievalResult().rrfChunks().stream().map(RrfHitResponse::from).toList()
        );
    }

    public record RouteHitResponse(
            int rank,
            double score,
            ChunkSummaryResponse chunk
    ) {
        static RouteHitResponse from(RetrievedChunk retrievedChunk) {
            return new RouteHitResponse(
                    retrievedChunk.rank(),
                    retrievedChunk.score(),
                    ChunkSummaryResponse.from(retrievedChunk.chunk())
            );
        }
    }

    public record RrfHitResponse(
            int finalRank,
            double rrfScore,
            Integer bm25Rank,
            Integer vectorRank,
            ChunkSummaryResponse chunk
    ) {
        static RrfHitResponse from(RrfRankedChunk rankedChunk) {
            return new RrfHitResponse(
                    rankedChunk.finalRank(),
                    rankedChunk.rrfScore(),
                    rankedChunk.bm25Rank(),
                    rankedChunk.vectorRank(),
                    ChunkSummaryResponse.from(rankedChunk.chunk())
            );
        }
    }

    public record ChunkSummaryResponse(
            String chunkId,
            String documentId,
            String documentName,
            KnowledgeDocumentType documentType,
            int chunkIndex,
            List<String> sectionPath,
            String contentPreview
    ) {
        private static final int PREVIEW_CHARS = 240;

        static ChunkSummaryResponse from(DocumentChunk chunk) {
            String normalized = chunk.content().replaceAll("\\s+", " ").strip();
            String preview = normalized.length() <= PREVIEW_CHARS
                    ? normalized
                    : normalized.substring(0, PREVIEW_CHARS) + "...";

            return new ChunkSummaryResponse(
                    chunk.chunkId(),
                    chunk.documentId(),
                    chunk.documentName(),
                    chunk.documentType(),
                    chunk.chunkIndex(),
                    chunk.sectionPath(),
                    preview
            );
        }
    }
}