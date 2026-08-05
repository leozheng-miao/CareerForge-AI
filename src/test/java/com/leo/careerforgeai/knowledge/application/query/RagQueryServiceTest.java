package com.leo.careerforgeai.knowledge.application.query;

import com.leo.careerforgeai.knowledge.application.answer.RagAnswerService;
import com.leo.careerforgeai.knowledge.application.context.ContextAssembler;
import com.leo.careerforgeai.knowledge.config.RagQueryProperties;
import com.leo.careerforgeai.knowledge.application.rerank.KnowledgeRerankingService;
import com.leo.careerforgeai.knowledge.application.retrieval.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswer;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswerStatus;
import com.leo.careerforgeai.knowledge.domain.answer.RagCitation;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankStatus;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    private static final String QUERY = "Java 并发中 Atomic 类适合什么场景？";

    @Mock
    private KnowledgeRetrievalService retrievalService;

    @Mock
    private KnowledgeRerankingService rerankingService;

    @Mock
    private ContextAssembler contextAssembler;

    @Mock
    private RagAnswerService answerService;

    private RagQueryService service;
    private RetrievalScope scope;
    private HybridRetrievalResult retrievalResult;
    private RerankedRetrievalResult rerankedResult;
    private AssembledContext context;
    private RagAnswer answer;

    @BeforeEach
    void setUp() {
        RagQueryProperties properties = new RagQueryProperties(5, 50, 5, false, 3_000);
        service = new RagQueryService(retrievalService, rerankingService, contextAssembler, answerService, properties);
        scope = new RetrievalScope("careerforge-career-materials", Set.of(), Set.of());

        DocumentChunk chunk = chunk();
        RetrievedChunk retrievedChunk = new RetrievedChunk(chunk, 1.0, 1);
        RetrievalResult routeResult = new RetrievalResult(List.of(retrievedChunk), 10);
        RetrievalComparisonResult comparisonResult = new RetrievalComparisonResult(routeResult, routeResult, "qwen3-embedding:0.6b", 1024, 20);
        RrfRankedChunk rankedChunk = new RrfRankedChunk(chunk, 1, 1, 2.0 / 61.0, 1);

        retrievalResult = new HybridRetrievalResult(comparisonResult, List.of(rankedChunk), 1);
        rerankedResult = new RerankedRetrievalResult(retrievalResult, List.of(rankedChunk), RerankStatus.DISABLED, 0, null, 0, 0, 0);
        context = new AssembledContext(List.of(chunk), chunk.retrievalText().length(), 3_000, 0, 0, ContextAssembler.VERSION);
        answer = new RagAnswer(RagAnswerStatus.ANSWERED, "Atomic 类适合单变量原子更新。", List.of(RagCitation.from(chunk)));
    }

    @Test
    void shouldExecuteCompleteRagQueryInOrder() {
        when(retrievalService.retrieveHybrid(QUERY, scope, 5, 50, 5)).thenReturn(retrievalResult);
        when(rerankingService.rerank(QUERY, retrievalResult, false)).thenReturn(rerankedResult);
        when(contextAssembler.assemble(rerankedResult.rankedChunks(), 3_000)).thenReturn(context);
        when(answerService.answer(QUERY, context)).thenReturn(answer);

        RagQueryResult result = service.query(QUERY, scope);

        assertThat(result.requestId()).isNotBlank();
        assertThat(result.retrievalResult()).isSameAs(retrievalResult);
        assertThat(result.rerankedResult()).isSameAs(rerankedResult);
        assertThat(result.context()).isSameAs(context);
        assertThat(result.answer()).isSameAs(answer);
        assertThat(result.totalDurationMs()).isGreaterThanOrEqualTo(0);

        InOrder order = inOrder(retrievalService, rerankingService, contextAssembler, answerService);
        order.verify(retrievalService).retrieveHybrid(QUERY, scope, 5, 50, 5);
        order.verify(rerankingService).rerank(QUERY, retrievalResult, false);
        order.verify(contextAssembler).assemble(rerankedResult.rankedChunks(), 3_000);
        order.verify(answerService).answer(QUERY, context);
    }

    @Test
    void shouldPropagateRetrievalFailureWithoutCallingLaterStages() {
        RuntimeException failure = new IllegalStateException("Elasticsearch unavailable");
        when(retrievalService.retrieveHybrid(QUERY, scope, 5, 50, 5)).thenThrow(failure);

        assertThatThrownBy(() -> service.query(QUERY, scope)).isSameAs(failure);
        verifyNoInteractions(rerankingService, contextAssembler, answerService);
    }

    @Test
    void shouldRejectInvalidQueryBeforeCallingPipeline() {
        assertThatThrownBy(() -> service.query(" ", scope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query 不能为空");

        verifyNoInteractions(retrievalService, rerankingService, contextAssembler, answerService);
    }

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