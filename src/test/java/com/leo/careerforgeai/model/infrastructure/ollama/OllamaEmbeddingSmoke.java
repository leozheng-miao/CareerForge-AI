package com.leo.careerforgeai.model.infrastructure.ollama;

import com.leo.careerforgeai.model.domain.embedding.EmbeddingPurpose;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * @program: CareerForge-AI
 * @description: 使用真实 Ollama 验证批量 Document Embedding、Query Embedding、1024 维度和向量归一化
 * @author: Miao Zheng
 * @date: 2026-08-03 12:40
 **/
class OllamaEmbeddingSmoke {

    private static final String MODEL = "qwen3-embedding:0.6b";
    private static final int DIMENSIONS = 1024;

    private final OllamaEmbeddingClient client = new OllamaEmbeddingClient(
            new OllamaEmbeddingProperties(URI.create("http://localhost:11434"), MODEL, DIMENSIONS),
            JsonMapper.builder().build(),
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    );

    @Test
    void shouldCallRealOllamaForDocumentAndQueryEmbedding() {
        String sharedText = "Java Agent 岗位需要掌握 RAG、Embedding、Elasticsearch 和模型调用。";
        EmbeddingResult documentResult = client.embed(new EmbeddingRequest(EmbeddingPurpose.DOCUMENT, List.of(
                sharedText,
                "面试通常会考察 Chunking、混合检索、RRF、引用校验和 RAG 评测。"
        )));
        EmbeddingResult queryResult = client.embed(new EmbeddingRequest(EmbeddingPurpose.QUERY, List.of(sharedText)));

        assertValidResult(documentResult, 2);
        assertValidResult(queryResult, 1);
        assertThat(queryResult.vectors().getFirst()).isNotEqualTo(documentResult.vectors().getFirst());

        double documentNorm = l2Norm(documentResult.vectors().getFirst());
        double queryNorm = l2Norm(queryResult.vectors().getFirst());
        double sameTextCosine = cosineSimilarity(documentResult.vectors().getFirst(), queryResult.vectors().getFirst());

        System.out.printf("purpose=DOCUMENT, model=%s, vectorCount=%d, dimensions=%d, durationMs=%d, firstL2Norm=%.8f%n", documentResult.model(), documentResult.vectors().size(), documentResult.dimensions(), documentResult.durationMs(), documentNorm);
        System.out.printf("purpose=QUERY, model=%s, vectorCount=%d, dimensions=%d, durationMs=%d, firstL2Norm=%.8f, instructionVersion=%s%n", queryResult.model(), queryResult.vectors().size(), queryResult.dimensions(), queryResult.durationMs(), queryNorm, Qwen3EmbeddingInputFormatter.QUERY_INSTRUCTION_VERSION);
        System.out.printf("sameRawTextDocumentQueryCosine=%.8f%n", sameTextCosine);
    }

    private void assertValidResult(EmbeddingResult result, int expectedCount) {
        assertThat(result.model()).isEqualTo(MODEL);
        assertThat(result.dimensions()).isEqualTo(DIMENSIONS);
        assertThat(result.vectors()).hasSize(expectedCount);
        assertThat(result.vectors()).allSatisfy(vector -> {
            assertThat(vector).hasSize(DIMENSIONS);
            assertThat(l2Norm(vector)).isBetween(0.999D, 1.001D);
        });
    }

    private double l2Norm(List<Float> vector) {
        return Math.sqrt(vector.stream().mapToDouble(value -> value * value).sum());
    }

    private double cosineSimilarity(List<Float> left, List<Float> right) {
        double dotProduct = 0;
        double leftSquaredNorm = 0;
        double rightSquaredNorm = 0;

        for (int index = 0; index < left.size(); index++) {
            dotProduct += left.get(index) * right.get(index);
            leftSquaredNorm += left.get(index) * left.get(index);
            rightSquaredNorm += right.get(index) * right.get(index);
        }

        return dotProduct / (Math.sqrt(leftSquaredNorm) * Math.sqrt(rightSquaredNorm));
    }
}