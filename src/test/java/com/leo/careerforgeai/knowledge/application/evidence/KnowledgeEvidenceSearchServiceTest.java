package com.leo.careerforgeai.knowledge.application.evidence;

import com.leo.careerforgeai.knowledge.application.context.ContextAssembler;
import com.leo.careerforgeai.knowledge.application.rerank.KnowledgeRerankingService;
import com.leo.careerforgeai.knowledge.application.retrieval.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.config.RagQueryProperties;
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
import com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.retrieval.KnowledgeRetrievalException;
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
 * @description: 验证证据搜索用例的检索、重排、组装顺序以及空证据和系统失败语义。
 * @author: Miao Zheng
 * @date: 2026-08-06 19:40
 **/
@ExtendWith(MockitoExtension.class)
class KnowledgeEvidenceSearchServiceTest {

    private static final String QUERY = "Java 并发中 Atomic 类适合什么场景？";

    @Mock
    private KnowledgeRetrievalService retrievalService;

    @Mock
    private KnowledgeRerankingService rerankingService;

    @Mock
    private ContextAssembler contextAssembler;

    private KnowledgeEvidenceSearchService service;
    private RetrievalScope scope;
    private HybridRetrievalResult retrievalResult;
    private RerankedRetrievalResult rerankedResult;
    private AssembledContext context;

    @BeforeEach
    void setUp() {
        RagQueryProperties properties = new RagQueryProperties(5, 50, 5, false, 3_000);
        service = new KnowledgeEvidenceSearchService(
                retrievalService, rerankingService, contextAssembler, properties);
        scope = new RetrievalScope("careerforge-career-materials", Set.of(), Set.of());

        DocumentChunk chunk = chunk();
        RetrievedChunk retrievedChunk = new RetrievedChunk(chunk, 1.0, 1);
        RetrievalResult routeResult = new RetrievalResult(List.of(retrievedChunk), 10);
        RetrievalComparisonResult comparisonResult = new RetrievalComparisonResult(
                routeResult, routeResult, "qwen3-embedding:0.6b", 1024, 20);
        RrfRankedChunk rankedChunk = new RrfRankedChunk(
                chunk, 1, 1, 2.0 / 61.0, 1);

        retrievalResult = new HybridRetrievalResult(
                comparisonResult, List.of(rankedChunk), 1);
        rerankedResult = new RerankedRetrievalResult(
                retrievalResult,
                List.of(rankedChunk),
                RerankStatus.DISABLED,
                0,
                null,
                0,
                0,
                0
        );
        context = new AssembledContext(
                List.of(chunk),
                chunk.retrievalText().length(),
                3_000,
                0,
                0,
                ContextAssembler.VERSION
        );
    }

    @Test
    @DisplayName("按检索重排和上下文组装顺序返回证据结果")
    void shouldSearchAndAssembleEvidenceInOrder() {
        when(retrievalService.retrieveHybrid(QUERY, scope, 5, 50, 5))
                .thenReturn(retrievalResult);
        when(rerankingService.rerank(QUERY, retrievalResult, false))
                .thenReturn(rerankedResult);
        when(contextAssembler.assemble(rerankedResult.rankedChunks(), 3_000))
                .thenReturn(context);

        KnowledgeEvidenceSearchResult result = service.search(QUERY, scope);

        assertThat(result.requestId()).isNotBlank();
        assertThat(result.retrievalResult()).isSameAs(retrievalResult);
        assertThat(result.rerankedResult()).isSameAs(rerankedResult);
        assertThat(result.context()).isSameAs(context);
        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.totalDurationMs()).isGreaterThanOrEqualTo(0);

        InOrder order = inOrder(retrievalService, rerankingService, contextAssembler);
        order.verify(retrievalService).retrieveHybrid(QUERY, scope, 5, 50, 5);
        order.verify(rerankingService).rerank(QUERY, retrievalResult, false);
        order.verify(contextAssembler).assemble(rerankedResult.rankedChunks(), 3_000);
    }

    @Test
    @DisplayName("空候选仍然返回正常的空证据结果")
    void shouldReturnEmptyEvidenceWithoutTreatingItAsSystemFailure() {
        RetrievalResult emptyRoute = new RetrievalResult(List.of(), 1);
        RetrievalComparisonResult emptyComparison = new RetrievalComparisonResult(
                emptyRoute, emptyRoute, "qwen3-embedding:0.6b", 1024, 2);
        HybridRetrievalResult emptyRetrieval = new HybridRetrievalResult(
                emptyComparison, List.of(), 0);
        RerankedRetrievalResult emptyReranked = new RerankedRetrievalResult(
                emptyRetrieval,
                List.of(),
                RerankStatus.SKIPPED_EMPTY,
                0,
                null,
                0,
                0,
                0
        );
        AssembledContext emptyContext = new AssembledContext(
                List.of(), 0, 3_000, 0, 0, ContextAssembler.VERSION);

        when(retrievalService.retrieveHybrid(QUERY, scope, 5, 50, 5))
                .thenReturn(emptyRetrieval);
        when(rerankingService.rerank(QUERY, emptyRetrieval, false))
                .thenReturn(emptyReranked);
        when(contextAssembler.assemble(List.of(), 3_000))
                .thenReturn(emptyContext);

        KnowledgeEvidenceSearchResult result = service.search(QUERY, scope);

        assertThat(result.candidateCount()).isZero();
        assertThat(result.context().chunks()).isEmpty();
        assertThat(result.context().usedContentChars()).isZero();
        assertThat(result.rerankedResult().status())
                .isEqualTo(RerankStatus.SKIPPED_EMPTY);
    }

    @Test
    @DisplayName("检索系统故障原样传播且不调用后续阶段")
    void shouldPropagateRetrievalFailureWithoutCallingLaterStages() {
        KnowledgeRetrievalException failure = new KnowledgeRetrievalException(
                "Elasticsearch unavailable");
        when(retrievalService.retrieveHybrid(QUERY, scope, 5, 50, 5))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.search(QUERY, scope))
                .isSameAs(failure);

        verifyNoInteractions(rerankingService, contextAssembler);
    }

    @Test
    @DisplayName("非法输入在检索调用前被拒绝")
    void shouldRejectInvalidInputBeforeCallingPipeline() {
        assertThatThrownBy(() -> service.search(" ", scope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query 不能为空");

        assertThatThrownBy(() -> service.search("x".repeat(2_001), scope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query 长度不能超过 2000");

        assertThatThrownBy(() -> service.search(QUERY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scope 不能为空");

        assertThatThrownBy(() -> service.search("bad\nrequest", QUERY, scope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId 包含非法字符");

        verifyNoInteractions(
                retrievalService,
                rerankingService,
                contextAssembler
        );
    }

    @Test
    @DisplayName("调用方提供的requestId贯穿证据搜索结果")
    void shouldPreserveCallerProvidedRequestId() {
        when(retrievalService.retrieveHybrid(QUERY, scope, 5, 50, 5))
                .thenReturn(retrievalResult);
        when(rerankingService.rerank(QUERY, retrievalResult, false))
                .thenReturn(rerankedResult);
        when(contextAssembler.assemble(rerankedResult.rankedChunks(), 3_000))
                .thenReturn(context);

        KnowledgeEvidenceSearchResult result = service.search(
                "tool-request-1", QUERY, scope);

        assertThat(result.requestId()).isEqualTo("tool-request-1");
    }

    /** 创建测试使用的可追溯知识片段。 */
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