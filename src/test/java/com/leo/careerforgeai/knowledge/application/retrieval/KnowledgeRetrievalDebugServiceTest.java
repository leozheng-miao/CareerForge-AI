package com.leo.careerforgeai.knowledge.application.retrieval;

import com.leo.careerforgeai.knowledge.config.RagQueryProperties;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-05 18:30
 **/
@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalDebugServiceTest {

    private static final String QUERY = "Elasticsearch 如何进行混合检索？";

    @Mock
    private KnowledgeRetrievalService retrievalService;

    private KnowledgeRetrievalDebugService service;
    private RetrievalScope scope;

    @BeforeEach
    void setUp() {
        RagQueryProperties properties = new RagQueryProperties(5, 50, 5, false, 3_000);
        service = new KnowledgeRetrievalDebugService(retrievalService, properties);
        scope = new RetrievalScope("careerforge-career-materials", Set.of(), Set.of());
    }

    @Test
    void shouldExecuteHybridRetrievalWithConfiguredParameters() {
        RetrievalResult bm25Result = new RetrievalResult(List.of(), 10);
        RetrievalResult vectorResult = new RetrievalResult(List.of(), 30);
        RetrievalComparisonResult comparisonResult = new RetrievalComparisonResult(
                bm25Result,
                vectorResult,
                "qwen3-embedding:0.6b",
                1024,
                20
        );
        HybridRetrievalResult hybridResult = new HybridRetrievalResult(comparisonResult, List.of(), 1);
        when(retrievalService.retrieveHybrid(QUERY, scope, 5, 50, 5)).thenReturn(hybridResult);

        RetrievalDebugResult result = service.debug(QUERY, scope);

        assertThat(result.requestId()).isNotBlank();
        assertThat(result.retrievalResult()).isSameAs(hybridResult);
        assertThat(result.totalDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldPropagateRetrievalFailure() {
        RuntimeException failure = new IllegalStateException("Ollama unavailable");
        when(retrievalService.retrieveHybrid(QUERY, scope, 5, 50, 5)).thenThrow(failure);

        assertThatThrownBy(() -> service.debug(QUERY, scope)).isSameAs(failure);
    }

    @Test
    void shouldRejectInvalidQueryBeforeRetrieval() {
        assertThatThrownBy(() -> service.debug(" ", scope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query 不能为空");

        verifyNoInteractions(retrievalService);
    }
}