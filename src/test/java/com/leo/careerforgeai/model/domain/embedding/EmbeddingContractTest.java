package com.leo.careerforgeai.model.domain.embedding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-02 23:32
 **/
class EmbeddingContractTest {

    @Test
    void shouldDefensivelyCopyRequestInputs() {
        List<String> mutableInputs = new ArrayList<>(List.of("文档一", "文档二"));

        EmbeddingRequest request = new EmbeddingRequest(EmbeddingPurpose.DOCUMENT, mutableInputs);
        mutableInputs.set(0, "已修改");

        assertThat(request.inputs()).containsExactly("文档一", "文档二");
        assertThatThrownBy(() -> request.inputs().add("文档三")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldDefensivelyCopyAndValidateResultVectors() {
        List<Float> mutableVector = new ArrayList<>(List.of(0.1F, 0.2F));
        List<List<Float>> mutableVectors = new ArrayList<>();
        mutableVectors.add(mutableVector);

        EmbeddingResult result = new EmbeddingResult("embedding-model", 2, mutableVectors, 15);
        mutableVector.set(0, 9.9F);
        mutableVectors.add(List.of(0.3F, 0.4F));

        assertThat(result.vectors()).containsExactly(List.of(0.1F, 0.2F));
        assertThatThrownBy(() -> result.vectors().add(List.of(0.5F, 0.6F))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.vectors().getFirst().add(0.3F)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidRequestsAndResults() {
        assertThatThrownBy(() -> new EmbeddingRequest(null, List.of("文本"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingRequest(EmbeddingPurpose.DOCUMENT, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingRequest(EmbeddingPurpose.QUERY, List.of(" "))).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EmbeddingResult("model", 2, List.of(List.of(0.1F)), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingResult("model", 2, List.of(List.of(Float.NaN, 0.1F)), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingResult("model", 2, List.of(List.of(0.1F, 0.2F)), -1)).isInstanceOf(IllegalArgumentException.class);
    }
}