package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-03 13:19
 **/
class KnowledgeChunkIndexDefinitionTest {

    @Test
    void shouldBuildVersionedStrictChunkMapping() {
        KnowledgeIndexProperties properties = new KnowledgeIndexProperties("careerforge-knowledge", "v1");
        CreateIndexRequest request = new KnowledgeChunkIndexDefinition(properties).createRequest(1024);
        Map<String, Property> fields = request.mappings().properties();

        assertThat(request.index()).isEqualTo("careerforge-knowledge-v1");
        assertThat(request.settings().numberOfShards()).isEqualTo("1");
        assertThat(request.settings().numberOfReplicas()).isEqualTo("0");
        assertThat(request.mappings().dynamic()).isEqualTo(DynamicMapping.Strict);

        assertThat(List.of("knowledgeBaseId", "documentId", "documentType", "sourceHash", "chunkId", "embeddingModel", "sectionPath"))
                .allSatisfy(field -> assertThat(fields.get(field).isKeyword()).isTrue());
        assertThat(List.of("embeddingDimensions", "chunkIndex", "startOffset", "endOffset"))
                .allSatisfy(field -> assertThat(fields.get(field).isInteger()).isTrue());

        assertThat(fields.get("content").isText()).isTrue();
        assertThat(fields.get("content").text().index()).isFalse();
        assertThat(fields.get("retrievalText").text().analyzer()).isEqualTo("cjk");
        assertThat(fields.get("retrievalText").text().searchAnalyzer()).isEqualTo("cjk");

        assertThat(fields.get("embedding").isDenseVector()).isTrue();
        assertThat(fields.get("embedding").denseVector().dims()).isEqualTo(1024);
        assertThat(fields.get("embedding").denseVector().index()).isTrue();
        assertThat(fields.get("embedding").denseVector().similarity()).isEqualTo(DenseVectorSimilarity.Cosine);
    }

    @Test
    void shouldRejectInvalidIndexConfigurationAndDimensions() {
        assertThatThrownBy(() -> new KnowledgeIndexProperties("CareerForge-Knowledge", "v1")).isInstanceOf(IllegalArgumentException.class);

        KnowledgeIndexProperties properties = new KnowledgeIndexProperties("careerforge-knowledge", "v1");
        assertThatThrownBy(() -> new KnowledgeChunkIndexDefinition(properties).createRequest(0)).isInstanceOf(IllegalArgumentException.class);
    }
}