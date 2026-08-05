package com.leo.careerforgeai.knowledge.infrastructure.rerank;

import com.leo.careerforgeai.knowledge.application.ChunkRerankException;
import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.ChunkRerankResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.ModelUsage;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeepSeekLlmChunkRerankerTest {

    private static final String A_ID = "a".repeat(64);
    private static final String B_ID = "b".repeat(64);
    private static final String C_ID = "c".repeat(64);

    private final ModelGateway modelGateway = mock(ModelGateway.class);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final DeepSeekLlmChunkReranker reranker = new DeepSeekLlmChunkReranker(modelGateway, JsonMapper.builder().build(), validator);

    /** 验证结构化请求、严格 ID 映射和模型排序。 */
    @Test
    void shouldBuildStructuredRequestAndMapOrderedCandidates() {
        List<RrfRankedChunk> candidates = List.of(candidate(A_ID, 1), candidate(B_ID, 2), candidate(C_ID, 3));
        when(modelGateway.chat(any())).thenReturn(response("{\"chunkIds\":[\"" + C_ID + "\",\"" + A_ID + "\",\"" + B_ID + "\"]}"));

        ChunkRerankResult result = reranker.rerank("什么内容最能解释 Java 并发？", candidates);

        assertThat(result.rankedChunks()).containsExactly(candidates.get(2), candidates.get(0), candidates.get(1));
        assertThat(result.rankedChunks().getFirst()).isSameAs(candidates.get(2));
        assertThat(result.model()).isEqualTo("configured-deepseek-model");
        assertThat(result.inputTokens()).isEqualTo(200);
        assertThat(result.outputTokens()).isEqualTo(30);
        assertThat(result.totalTokens()).isEqualTo(230);

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelGateway).chat(captor.capture());
        ModelRequest request = captor.getValue();

        assertThat(request.outputFormat()).isEqualTo(ModelOutputFormat.JSON_OBJECT);
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo(ModelRole.SYSTEM);
        assertThat(request.messages().get(1).role()).isEqualTo(ModelRole.USER);
        assertThat(request.messages().get(1).content()).contains(A_ID, B_ID, C_ID, "什么内容最能解释 Java 并发？");
        assertThat(request.messages().get(1).content()).doesNotContain("rrfRank", "rrfScore");
    }

    /** 验证非法 JSON、未知、重复、遗漏和额外字段都会被拒绝。 */
    @ParameterizedTest
    @MethodSource("invalidOutputs")
    void shouldRejectUntrustedModelOutput(String output) {
        List<RrfRankedChunk> candidates = List.of(candidate(A_ID, 1), candidate(B_ID, 2));
        when(modelGateway.chat(any())).thenReturn(response(output));

        assertThatThrownBy(() -> reranker.rerank("Java 并发", candidates)).isInstanceOf(ChunkRerankException.class);
    }

    /** 验证空候选不调用模型，非法输入在模型调用前失败。 */
    @Test
    void shouldHandleEmptyCandidatesAndRejectInvalidInput() {
        ChunkRerankResult emptyResult = reranker.rerank("Java 并发", List.of());

        assertThat(emptyResult.rankedChunks()).isEmpty();
        assertThat(emptyResult.model()).isNull();
        assertThat(emptyResult.totalTokens()).isZero();

        List<RrfRankedChunk> duplicateCandidates = List.of(candidate(A_ID, 1), candidate(A_ID, 2));
        List<RrfRankedChunk> tooManyCandidates = IntStream.rangeClosed(1, 21)
                .mapToObj(index -> candidate(String.format("%064x", index), index))
                .toList();

        assertThatThrownBy(() -> reranker.rerank(" ", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reranker.rerank("Java 并发", duplicateCandidates)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reranker.rerank("Java 并发", tooManyCandidates)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(modelGateway);
    }

    /** 验证模型调用异常被转换为 Rerank 专用异常。 */
    @Test
    void shouldWrapModelGatewayFailure() {
        RuntimeException failure = new RuntimeException("provider unavailable");
        when(modelGateway.chat(any())).thenThrow(failure);

        assertThatThrownBy(() -> reranker.rerank("Java 并发", List.of(candidate(A_ID, 1))))
                .isInstanceOf(ChunkRerankException.class)
                .hasCause(failure);
    }

    private static Stream<String> invalidOutputs() {
        return Stream.of(
                "not-json",
                "{\"chunkIds\":[\"" + A_ID + "\",\"" + C_ID + "\"]}",
                "{\"chunkIds\":[\"" + A_ID + "\",\"" + A_ID + "\"]}",
                "{\"chunkIds\":[\"" + A_ID + "\"]}",
                "{\"chunkIds\":[\"" + A_ID + "\",\"" + B_ID + "\"],\"reason\":\"extra\"}",
                "{\"chunkIds\":[\"\",\"" + B_ID + "\"]}"
        );
    }

    private ModelResponse response(String content) {
        return new ModelResponse("rerank-request-1", "configured-deepseek-model", content, new ModelUsage(200, 30, 230));
    }

    private RrfRankedChunk candidate(String chunkId, int rank) {
        String content = "候选正文-" + chunkId.substring(0, 4);
        DocumentChunk chunk = new DocumentChunk(
                "careerforge",
                "document-1",
                "测试文档",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                "测试文档.md",
                "f".repeat(64),
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                chunkId,
                rank - 1,
                List.of("测试文档", "Java 并发"),
                0,
                content.length(),
                content
        );
        return new RrfRankedChunk(chunk, rank, null, 1D / (60 + rank), rank);
    }
}