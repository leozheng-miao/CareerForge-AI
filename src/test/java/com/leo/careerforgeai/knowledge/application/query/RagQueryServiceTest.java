package com.leo.careerforgeai.knowledge.application.query;

import com.leo.careerforgeai.knowledge.application.answer.RagAnswerService;
import com.leo.careerforgeai.knowledge.application.context.ContextAssembler;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchResult;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswer;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswerStatus;
import com.leo.careerforgeai.knowledge.domain.answer.RagCitation;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankStatus;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证完整RAG查询复用证据搜索并只负责最终回答生成。
 * @author: Miao Zheng
 * @date: 2026-08-06 20:00
 **/
@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    private static final String QUERY = "Java 并发中 Atomic 类适合什么场景？";

    @Mock
    private KnowledgeEvidenceSearchService evidenceSearchService;

    @Mock
    private RagAnswerService answerService;

    private RagQueryService service;
    private RetrievalScope scope;
    private KnowledgeEvidenceSearchResult evidenceResult;
    private RagAnswer answer;

    @BeforeEach
    void setUp() {
        service = new RagQueryService(evidenceSearchService, answerService);
        scope = new RetrievalScope("careerforge-career-materials", Set.of(), Set.of());

        DocumentChunk chunk = chunk();
        RetrievedChunk retrievedChunk = new RetrievedChunk(chunk, 1.0, 1);
        RetrievalResult routeResult = new RetrievalResult(List.of(retrievedChunk), 10);
        RetrievalComparisonResult comparisonResult = new RetrievalComparisonResult(
                routeResult, routeResult, "qwen3-embedding:0.6b", 1024, 20);
        RrfRankedChunk rankedChunk = new RrfRankedChunk(chunk, 1, 1, 2.0 / 61.0, 1);
        HybridRetrievalResult retrievalResult = new HybridRetrievalResult(
                comparisonResult, List.of(rankedChunk), 1);
        RerankedRetrievalResult rerankedResult = new RerankedRetrievalResult(
                retrievalResult,
                List.of(rankedChunk),
                RerankStatus.DISABLED,
                0,
                null,
                0,
                0,
                0
        );
        AssembledContext context = new AssembledContext(
                List.of(chunk),
                chunk.retrievalText().length(),
                3_000,
                0,
                0,
                ContextAssembler.VERSION
        );

        evidenceResult = new KnowledgeEvidenceSearchResult(
                "request-1", retrievalResult, rerankedResult, context, 40);
        answer = new RagAnswer(
                RagAnswerStatus.ANSWERED,
                "Atomic 类适合单变量原子更新。",
                List.of(RagCitation.from(chunk))
        );
    }

    @Test
    @DisplayName("复用证据搜索结果并生成最终RAG回答")
    void shouldGenerateAnswerFromSharedEvidenceSearchResult() {
        when(evidenceSearchService.search(QUERY, scope)).thenReturn(evidenceResult);
        when(answerService.answer(QUERY, evidenceResult.context())).thenReturn(answer);

        RagQueryResult result = service.query(QUERY, scope);

        assertThat(result.requestId()).isEqualTo("request-1");
        assertThat(result.retrievalResult()).isSameAs(evidenceResult.retrievalResult());
        assertThat(result.rerankedResult()).isSameAs(evidenceResult.rerankedResult());
        assertThat(result.context()).isSameAs(evidenceResult.context());
        assertThat(result.answer()).isSameAs(answer);
        assertThat(result.totalDurationMs()).isGreaterThanOrEqualTo(0);

        InOrder order = inOrder(evidenceSearchService, answerService);
        order.verify(evidenceSearchService).search(QUERY, scope);
        order.verify(answerService).answer(QUERY, evidenceResult.context());
    }

    @Test
    @DisplayName("证据搜索失败时不调用回答模型")
    void shouldPropagateEvidenceFailureWithoutGeneratingAnswer() {
        RuntimeException failure = new IllegalStateException("retrieval failed");
        when(evidenceSearchService.search(QUERY, scope)).thenThrow(failure);

        assertThatThrownBy(() -> service.query(QUERY, scope)).isSameAs(failure);
        verifyNoInteractions(answerService);
    }

    @Test
    @DisplayName("回答生成失败时传播异常且不重复搜索证据")
    void shouldPropagateAnswerFailureAfterEvidenceSearch() {
        RuntimeException failure = new IllegalStateException("answer failed");
        when(evidenceSearchService.search(QUERY, scope)).thenReturn(evidenceResult);
        when(answerService.answer(QUERY, evidenceResult.context())).thenThrow(failure);

        assertThatThrownBy(() -> service.query(QUERY, scope)).isSameAs(failure);

        InOrder order = inOrder(evidenceSearchService, answerService);
        order.verify(evidenceSearchService).search(QUERY, scope);
        order.verify(answerService).answer(QUERY, evidenceResult.context());
    }

    /** 创建测试使用的可引用知识片段。 */
    private DocumentChunk chunk() {
        return new DocumentChunk(
                "careerforge-career-materials",
                "ai-interview-summary",
                "AI应用开发_面经汇总.md",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                "AI应用开发_面经汇总.md",
                "a".repeat(64),
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                "b".repeat(64),
                0,
                List.of("Java 并发"),
                0,
                20,
                "Atomic 类适合单变量原子更新。"
        );
    }
}