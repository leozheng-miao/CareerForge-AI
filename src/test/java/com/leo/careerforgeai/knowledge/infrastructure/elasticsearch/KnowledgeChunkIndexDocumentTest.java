package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch;

import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-03 13:47
 **/
class KnowledgeChunkIndexDocumentTest {

    @Test
    void shouldCombineChunkAndEmbeddingIntoIndexDocument() {
        List<Float> vector = new ArrayList<>(List.of(0.1F, 0.2F, 0.3F));
        KnowledgeChunkIndexDocument document = KnowledgeChunkIndexDocument.from(chunk(), "qwen3-embedding:0.6b", 3, vector);
        vector.set(0, 9.0F);

        assertThat(document.knowledgeBaseId()).isEqualTo("careerforge");
        assertThat(document.documentType()).isEqualTo("JOB_DESCRIPTION");
        assertThat(document.chunkId()).isEqualTo("b".repeat(64));
        assertThat(document.sectionPath()).containsExactly("岗位汇总", "Java 开发");
        assertThat(document.content()).isEqualTo("要求掌握 Java 并发");
        assertThat(document.retrievalText()).isEqualTo("岗位汇总 > Java 开发\n\n要求掌握 Java 并发");
        assertThat(document.embeddingModel()).isEqualTo("qwen3-embedding:0.6b");
        assertThat(document.embeddingDimensions()).isEqualTo(3);
        assertThat(document.embedding()).containsExactly(0.1F, 0.2F, 0.3F);
    }

    @Test
    void shouldRejectInvalidEmbedding() {
        assertThatThrownBy(() -> KnowledgeChunkIndexDocument.from(chunk(), "qwen3-embedding:0.6b", 2, List.of(0.1F)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding 维度必须等于 embeddingDimensions");

        assertThatThrownBy(() -> KnowledgeChunkIndexDocument.from(chunk(), "qwen3-embedding:0.6b", 1, List.of(Float.NaN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding 不能包含 null、NaN 或 Infinity");
    }

    private DocumentChunk chunk() {
        return new DocumentChunk(
                "careerforge",
                "job-summary",
                "岗位汇总",
                KnowledgeDocumentType.JOB_DESCRIPTION,
                "岗位汇总.md",
                "a".repeat(64),
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                "b".repeat(64),
                0,
                List.of("岗位汇总", "Java 开发"),
                10,
                21,
                "要求掌握 Java 并发"
        );
    }
}