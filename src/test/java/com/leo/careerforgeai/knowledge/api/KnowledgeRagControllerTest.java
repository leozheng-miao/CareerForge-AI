package com.leo.careerforgeai.knowledge.api;

import com.leo.careerforgeai.knowledge.api.dto.RagQueryRequest;
import com.leo.careerforgeai.knowledge.api.dto.RagQueryResponse;
import com.leo.careerforgeai.knowledge.application.RagQueryResult;
import com.leo.careerforgeai.knowledge.application.RagQueryService;
import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswer;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswerStatus;
import com.leo.careerforgeai.knowledge.domain.answer.RagCitation;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RerankStatus;
import com.leo.careerforgeai.knowledge.domain.retrieval.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import com.leo.careerforgeai.knowledge.infrastructure.document.KnowledgeSourceProperties;
import com.leo.careerforgeai.shared.web.BaseResponse;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRagControllerTest {

    private static final String QUERY = "Atomic 类适合什么场景？";

    @Mock
    private RagQueryService ragQueryService;

    private KnowledgeRagController controller;

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
        controller = new KnowledgeRagController(ragQueryService, sourceProperties);
    }

    @Test
    void shouldBuildServerControlledScopeAndMapAnswerResponse() {
        RetrievalScope expectedScope = new RetrievalScope(
                "careerforge-career-materials",
                Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE),
                Set.of("ai-interview-summary")
        );

        RagCitation citation = new RagCitation(
                "b".repeat(64),
                "ai-interview-summary",
                "AI应用开发_面经汇总.md",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                "AI应用开发_面经汇总.md",
                "a".repeat(64),
                3,
                List.of("Java 并发", "Atomic 类"),
                100,
                200
        );
        RagAnswer answer = new RagAnswer(
                RagAnswerStatus.ANSWERED,
                "Atomic 类适合单变量原子更新。",
                List.of(citation)
        );

        HybridRetrievalResult retrievalResult = mock(HybridRetrievalResult.class);
        RerankedRetrievalResult rerankedResult = mock(RerankedRetrievalResult.class);
        AssembledContext context = mock(AssembledContext.class);
        RagQueryResult queryResult = mock(RagQueryResult.class);

        when(retrievalResult.rrfChunks()).thenReturn(List.of(mock(RrfRankedChunk.class)));
        when(rerankedResult.status()).thenReturn(RerankStatus.DISABLED);
        when(context.chunks()).thenReturn(List.of(mock(DocumentChunk.class)));
        when(queryResult.requestId()).thenReturn("request-1");
        when(queryResult.retrievalResult()).thenReturn(retrievalResult);
        when(queryResult.rerankedResult()).thenReturn(rerankedResult);
        when(queryResult.context()).thenReturn(context);
        when(queryResult.answer()).thenReturn(answer);
        when(queryResult.totalDurationMs()).thenReturn(120L);
        when(ragQueryService.query(QUERY, expectedScope)).thenReturn(queryResult);

        RagQueryRequest request = new RagQueryRequest(
                QUERY,
                Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE),
                Set.of("ai-interview-summary")
        );
        BaseResponse<RagQueryResponse> response = controller.query(request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().requestId()).isEqualTo("request-1");
        assertThat(response.getData().status()).isEqualTo(RagAnswerStatus.ANSWERED);
        assertThat(response.getData().answer()).isEqualTo("Atomic 类适合单变量原子更新。");
        assertThat(response.getData().rerankStatus()).isEqualTo(RerankStatus.DISABLED);
        assertThat(response.getData().retrievedCandidateCount()).isEqualTo(1);
        assertThat(response.getData().contextChunkCount()).isEqualTo(1);
        assertThat(response.getData().citations()).singleElement().satisfies(mapped -> {
            assertThat(mapped.chunkId()).isEqualTo("b".repeat(64));
            assertThat(mapped.documentName()).isEqualTo("AI应用开发_面经汇总.md");
            assertThat(mapped.sectionPath()).containsExactly("Java 并发", "Atomic 类");
            assertThat(mapped.startOffset()).isEqualTo(100);
            assertThat(mapped.endOffset()).isEqualTo(200);
        });

        verify(ragQueryService).query(QUERY, expectedScope);
    }

    @Test
    void shouldDeclareValidationForInvalidRequest() {
        RagQueryRequest request = new RagQueryRequest(
                " ",
                Set.of(),
                Set.of()
        );

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("query"));
        }
    }
}