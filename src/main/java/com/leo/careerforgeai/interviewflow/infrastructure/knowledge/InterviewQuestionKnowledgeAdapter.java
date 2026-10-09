package com.leo.careerforgeai.interviewflow.infrastructure.knowledge;

import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.KnowledgeQuestionCandidate;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.SourceReference;
import com.leo.careerforgeai.knowledge.application.retrieval.Bm25Retriever;
import com.leo.careerforgeai.knowledge.application.retrieval.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.knowledge.config.RagQueryProperties;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 从管理员公共面经执行混合检索并在失败时降级BM25
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Component
@Slf4j
public final class InterviewQuestionKnowledgeAdapter
        implements InterviewQuestionKnowledgePort {

    private static final int MAX_LIMIT = 10;

    private final KnowledgeRetrievalService retrievalService;
    private final Bm25Retriever bm25Retriever;
    private final KnowledgeSourceProperties sourceProperties;
    private final RagQueryProperties queryProperties;

    public InterviewQuestionKnowledgeAdapter(
            KnowledgeRetrievalService retrievalService,
            Bm25Retriever bm25Retriever,
            KnowledgeSourceProperties sourceProperties,
            RagQueryProperties queryProperties
    ) {
        this.retrievalService = Objects.requireNonNull(
                retrievalService,
                "retrievalService不能为空"
        );
        this.bm25Retriever = Objects.requireNonNull(
                bm25Retriever,
                "bm25Retriever不能为空"
        );
        this.sourceProperties = Objects.requireNonNull(
                sourceProperties,
                "sourceProperties不能为空"
        );
        this.queryProperties = Objects.requireNonNull(
                queryProperties,
                "queryProperties不能为空"
        );
    }

    @Override
    public List<KnowledgeQuestionCandidate> search(
            String direction,
            String objective,
            int limit
    ) {
        requireText(direction, "direction");
        requireText(objective, "objective");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit必须在1到10之间");
        }

        String query = direction + "\n" + objective;
        RetrievalScope scope = new RetrievalScope(
                sourceProperties.getKnowledgeBaseId(),
                Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE),
                Set.of()
        );
        try {
            int candidateTopK = Math.max(
                    queryProperties.getCandidateTopK(),
                    limit
            );
            return mapChunks(
                    retrievalService.retrieveHybrid(
                                    query,
                                    scope,
                                    candidateTopK,
                                    Math.max(
                                            queryProperties.getNumCandidates(),
                                            candidateTopK
                                    ),
                                    limit
                            ).rrfChunks().stream()
                            .map(ranked -> ranked.chunk())
                            .toList(),
                    limit
            );
        } catch (RuntimeException hybridFailure) {
            log.warn(
                    "快速面试混合检索失败，降级BM25，objective={}, errorType={}",
                    objective,
                    hybridFailure.getClass().getSimpleName()
            );
            try {
                return mapChunks(
                        bm25Retriever.retrieve(query, scope, limit)
                                .chunks().stream()
                                .map(retrieved -> retrieved.chunk())
                                .toList(),
                        limit
                );
            } catch (RuntimeException bm25Failure) {
                log.warn(
                        "快速面试BM25检索失败，将使用管理员备用题，objective={}, errorType={}",
                        objective,
                        bm25Failure.getClass().getSimpleName()
                );
                return List.of();
            }
        }
    }

    private List<KnowledgeQuestionCandidate> mapChunks(
            List<DocumentChunk> chunks,
            int limit
    ) {
        Map<String, KnowledgeQuestionCandidate> unique =
                new LinkedHashMap<>();
        for (DocumentChunk chunk : chunks) {
            if (chunk == null || unique.containsKey(chunk.chunkId())) {
                continue;
            }
            String content = truncate(
                    chunk.content().strip(),
                    queryProperties.getMaxContentChars()
            );
            if (content.isBlank()) {
                continue;
            }
            unique.put(
                    chunk.chunkId(),
                    new KnowledgeQuestionCandidate(
                            content,
                            List.of(new SourceReference(
                                    chunk.knowledgeBaseId(),
                                    chunk.documentId(),
                                    chunk.chunkId(),
                                    chunk.sourceHash(),
                                    chunk.startOffset(),
                                    chunk.endOffset()
                            ))
                    )
            );
            if (unique.size() == limit) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }
}
