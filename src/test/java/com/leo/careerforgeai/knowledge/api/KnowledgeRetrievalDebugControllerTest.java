package com.leo.careerforgeai.knowledge.api;

import com.leo.careerforgeai.knowledge.api.dto.RetrievalDebugRequest;
import com.leo.careerforgeai.knowledge.api.dto.RetrievalDebugResponse;
import com.leo.careerforgeai.knowledge.application.retrieval.KnowledgeRetrievalDebugService;
import com.leo.careerforgeai.knowledge.application.retrieval.RetrievalDebugResult;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.shared.web.BaseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalDebugControllerTest {

    private static final String QUERY = "Elasticsearch 如何进行混合检索？";

    @Mock
    private KnowledgeRetrievalDebugService debugService;

    private KnowledgeRetrievalDebugController controller;

    @BeforeEach
    void setUp() {
        KnowledgeSourceProperties sourceProperties = new KnowledgeSourceProperties(
                "careerforge-career-materials",
                Path.of("."),
                List.of(new KnowledgeSourceProperties.DocumentDefinition(
                        "ai-interview-summary",
                        "AI应用开发_面经汇总.md",
                        KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                        "AI应用开发_面经汇总.md"
                ))
        );
        controller = new KnowledgeRetrievalDebugController(debugService, sourceProperties);
    }

    @Test
    void shouldBuildScopeAndMapThreeRetrievalRankings() {
        RetrievalScope expectedScope = new RetrievalScope(
                "careerforge-career-materials",
                Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE),
                Set.of()
        );

        DocumentChunk chunk = new DocumentChunk(
                "careerforge-career-materials",
                "ai-interview-summary",
                "AI应用开发_面经汇总.md",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                "AI应用开发_面经汇总.md",
                "a".repeat(64),
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                "b".repeat(64),
                3,
                List.of("Elasticsearch", "混合检索"),
                100,
                200,
                "Elasticsearch 可以结合 text 字段和 dense_vector 字段执行混合检索。"
        );

        RetrievalResult bm25Result = new RetrievalResult(
                List.of(new RetrievedChunk(chunk, 7.5, 1)),
                12
        );
        RetrievalResult vectorResult = new RetrievalResult(
                List.of(new RetrievedChunk(chunk, 0.86, 1)),
                18
        );
        RetrievalComparisonResult comparisonResult = new RetrievalComparisonResult(
                bm25Result,
                vectorResult,
                "qwen3-embedding:0.6b",
                1024,
                25
        );
        HybridRetrievalResult hybridResult = new HybridRetrievalResult(
                comparisonResult,
                List.of(new RrfRankedChunk(chunk, 1, 1, 2.0 / 61.0, 1)),
                1
        );
        RetrievalDebugResult debugResult = new RetrievalDebugResult("request-1", hybridResult, 56);
        when(debugService.debug(QUERY, expectedScope)).thenReturn(debugResult);

        RetrievalDebugRequest request = new RetrievalDebugRequest(
                QUERY,
                Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE),
                Set.of()
        );
        BaseResponse<RetrievalDebugResponse> response = controller.debug(request);
        RetrievalDebugResponse data = response.getData();

        assertThat(response.getCode()).isZero();
        assertThat(data.requestId()).isEqualTo("request-1");
        assertThat(data.embeddingModel()).isEqualTo("qwen3-embedding:0.6b");
        assertThat(data.embeddingDimensions()).isEqualTo(1024);
        assertThat(data.bm25DurationMs()).isEqualTo(12);
        assertThat(data.vectorSearchDurationMs()).isEqualTo(18);
        assertThat(data.vectorTotalDurationMs()).isEqualTo(43);
        assertThat(data.fusionDurationMs()).isEqualTo(1);
        assertThat(data.bm25Hits()).singleElement().satisfies(hit -> {
            assertThat(hit.rank()).isEqualTo(1);
            assertThat(hit.score()).isEqualTo(7.5);
            assertThat(hit.chunk().chunkId()).isEqualTo("b".repeat(64));
        });
        assertThat(data.vectorHits()).singleElement().satisfies(hit -> {
            assertThat(hit.rank()).isEqualTo(1);
            assertThat(hit.score()).isEqualTo(0.86);
        });
        assertThat(data.rrfHits()).singleElement().satisfies(hit -> {
            assertThat(hit.finalRank()).isEqualTo(1);
            assertThat(hit.bm25Rank()).isEqualTo(1);
            assertThat(hit.vectorRank()).isEqualTo(1);
            assertThat(hit.chunk().contentPreview()).contains("dense_vector");
        });

        verify(debugService).debug(QUERY, expectedScope);
    }
}