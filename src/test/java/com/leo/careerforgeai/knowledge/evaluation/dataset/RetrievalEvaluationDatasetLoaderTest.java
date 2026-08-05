package com.leo.careerforgeai.knowledge.evaluation.dataset;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalEvaluationDatasetLoaderTest {

    private final RetrievalEvaluationDatasetLoader loader = new RetrievalEvaluationDatasetLoader(JsonMapper.builder().build());

    @Test
    void shouldLoadFrozenEvaluationDataset() {
        RetrievalEvaluationDataset dataset = loader.load();

        assertThat(dataset.cases()).hasSize(20);
        assertThat(dataset.cases()).extracting(RetrievalEvaluationDataset.EvaluationCase::caseId).doesNotHaveDuplicates();
        assertThat(dataset.cases()).extracting(RetrievalEvaluationDataset.EvaluationCase::query).doesNotHaveDuplicates();
        assertThat(dataset.cases()).extracting(RetrievalEvaluationDataset.EvaluationCase::queryType)
                .contains(RetrievalEvaluationDataset.QueryType.values());
        assertThat(dataset.cases())
                .filteredOn(evaluationCase -> evaluationCase.expectedAnswerability() == RetrievalEvaluationDataset.ExpectedAnswerability.UNANSWERABLE)
                .hasSize(4)
                .allSatisfy(evaluationCase -> assertThat(evaluationCase.relevantChunkIds()).isEmpty());
    }

    @Test
    void shouldRejectUnknownJsonProperty() throws IOException {
        String validJson;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(RetrievalEvaluationDatasetLoader.DATASET_RESOURCE)) {
            if (input == null) throw new IllegalStateException("测试资源不存在");
            validJson = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String invalidJson = validJson.replace(
                "\"schemaVersion\": \"rag-retrieval-evaluation-v1\"",
                "\"unknownField\": true,\n  \"schemaVersion\": \"rag-retrieval-evaluation-v1\""
        );

        assertThatThrownBy(() -> loader.read(new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(EvaluationDatasetException.class)
                .hasMessageContaining("解析或校验失败");
    }

    @Test
    void shouldRejectContradictoryAnswerabilityLabel() {
        assertThatThrownBy(() -> new RetrievalEvaluationDataset.EvaluationCase(
                "rag-eval-999",
                "测试问题",
                RetrievalEvaluationDataset.QueryType.UNANSWERABLE,
                RetrievalEvaluationDataset.ExpectedAnswerability.UNANSWERABLE,
                java.util.List.of("a".repeat(64)),
                "测试错误标注"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedAnswerability 与 relevantChunkIds 不一致");
    }
}