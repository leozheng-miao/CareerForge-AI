package com.leo.careerforgeai.model.infrastructure.ollama;

import com.leo.careerforgeai.model.domain.embedding.EmbeddingPurpose;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-02 23:43
 **/
class Qwen3EmbeddingInputFormatterTest {
    private final Qwen3EmbeddingInputFormatter formatter = new Qwen3EmbeddingInputFormatter();

    @Test
    void shouldKeepDocumentInputsUnchanged() {
        EmbeddingRequest request = new EmbeddingRequest(EmbeddingPurpose.DOCUMENT, List.of("标题\n\n文档正文", "第二个文档"));

        assertThat(formatter.format(request)).containsExactly("标题\n\n文档正文", "第二个文档");
    }

    @Test
    void shouldAddVersionedInstructionOnlyToQueries() {
        EmbeddingRequest request = new EmbeddingRequest(EmbeddingPurpose.QUERY, List.of("Java Agent 岗位需要哪些能力？", "面试会考察哪些 RAG 问题？"));

        assertThat(Qwen3EmbeddingInputFormatter.QUERY_INSTRUCTION_VERSION).isEqualTo("qwen3-career-retrieval-v1");
        assertThat(formatter.format(request)).containsExactly(
                "Instruct: " + Qwen3EmbeddingInputFormatter.QUERY_INSTRUCTION + "\nQuery:Java Agent 岗位需要哪些能力？",
                "Instruct: " + Qwen3EmbeddingInputFormatter.QUERY_INSTRUCTION + "\nQuery:面试会考察哪些 RAG 问题？"
        );
    }

    @Test
    void shouldRejectNullRequest() {
        assertThatThrownBy(() -> formatter.format(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("request 不能为空");
    }
}