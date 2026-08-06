package com.leo.careerforgeai.knowledge.application.query;

import com.leo.careerforgeai.knowledge.application.answer.RagAnswerService;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchResult;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswer;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * @program: CareerForge-AI
 * @description: 复用统一证据搜索用例并生成带引用的完整 RAG 回答。
 * @author: Miao Zheng
 * @date: 2026-08-06 20:00
 **/
@Service
@Slf4j
public class RagQueryService {

    private final KnowledgeEvidenceSearchService evidenceSearchService;
    private final RagAnswerService answerService;

    public RagQueryService(
            KnowledgeEvidenceSearchService evidenceSearchService,
            RagAnswerService answerService
    ) {
        this.evidenceSearchService = evidenceSearchService;
        this.answerService = answerService;
    }

    /** 执行统一证据搜索并生成完整RAG回答。 */
    public RagQueryResult query(String query, RetrievalScope scope) {
        long startNanos = System.nanoTime();
        KnowledgeEvidenceSearchResult evidenceResult = evidenceSearchService.search(query, scope);

        try {
            RagAnswer answer = answerService.answer(query, evidenceResult.context());
            long totalDurationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            log.info("RAG查询完成，requestId={}, knowledgeBaseId={}, answerStatus={}, rrfCandidates={}, rerankStatus={}, contextChunks={}, citations={}, totalDurationMs={}", evidenceResult.requestId(), scope.knowledgeBaseId(), answer.status(), evidenceResult.candidateCount(), evidenceResult.rerankedResult().status(), evidenceResult.context().chunks().size(), answer.citations().size(), totalDurationMs);
            return new RagQueryResult(
                    evidenceResult.requestId(),
                    evidenceResult.retrievalResult(),
                    evidenceResult.rerankedResult(),
                    evidenceResult.context(),
                    answer,
                    totalDurationMs
            );
        } catch (RuntimeException exception) {
            long totalDurationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.error("RAG回答生成失败，requestId={}, knowledgeBaseId={}, exceptionType={}, totalDurationMs={}", evidenceResult.requestId(), scope.knowledgeBaseId(), exception.getClass().getSimpleName(), totalDurationMs);
            throw exception;
        }
    }
}