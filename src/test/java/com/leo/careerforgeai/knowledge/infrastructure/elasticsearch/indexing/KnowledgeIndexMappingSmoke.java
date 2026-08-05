package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.indexing;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import com.leo.careerforgeai.knowledge.config.KnowledgeIndexProperties;
import com.leo.careerforgeai.model.infrastructure.ollama.OllamaEmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-03 13:24
 **/
@SpringBootTest(properties = {"careerforge.model.base-url=http://localhost", "careerforge.model.api-key=smoke-test-placeholder", "careerforge.model.name=smoke-test-placeholder"})
class KnowledgeIndexMappingSmoke {

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private KnowledgeIndexProperties indexProperties;

    @Autowired
    private KnowledgeChunkIndexDefinition indexDefinition;

    @Autowired
    private OllamaEmbeddingProperties embeddingProperties;

    @Test
    void shouldCreateAndReadRealKnowledgeIndexMapping() throws IOException {
        String indexName = indexProperties.concreteIndexName();
        boolean existed = client.indices().exists(request -> request.index(indexName)).value();

        if (!existed) {
            CreateIndexResponse createResponse = client.indices().create(indexDefinition.createRequest(embeddingProperties.getDimensions()));
            assertThat(createResponse.acknowledged()).isTrue();
            assertThat(createResponse.shardsAcknowledged()).isTrue();
        }

        IndexMappingRecord mappingRecord = client.indices().getMapping(request -> request.index(indexName)).get(indexName);
        assertThat(mappingRecord).isNotNull();

        TypeMapping mapping = mappingRecord.mappings();
        Map<String, Property> fields = mapping.properties();
        String retrievalAnalyzer = fields.get("retrievalText").text().analyzer();
        String retrievalSearchAnalyzer = fields.get("retrievalText").text().searchAnalyzer();

        assertThat(mapping.dynamic()).isEqualTo(DynamicMapping.Strict);
        assertThat(fields.get("knowledgeBaseId").isKeyword()).isTrue();
        assertThat(fields.get("chunkIndex").isInteger()).isTrue();
        assertThat(fields.get("content").text().index()).isFalse();
        assertThat(retrievalAnalyzer).isEqualTo("cjk");
        assertThat(retrievalSearchAnalyzer == null ? retrievalAnalyzer : retrievalSearchAnalyzer).isEqualTo("cjk");        assertThat(fields.get("embedding").denseVector().dims()).isEqualTo(embeddingProperties.getDimensions());
        assertThat(fields.get("embedding").denseVector().index()).isTrue();
        assertThat(fields.get("embedding").denseVector().similarity()).isEqualTo(DenseVectorSimilarity.Cosine);

        System.out.printf("index=%s, created=%s, dynamic=%s, fieldCount=%d, retrievalAnalyzer=%s, embeddingDimensions=%d, vectorSimilarity=%s%n", indexName, !existed, mapping.dynamic(), fields.size(), fields.get("retrievalText").text().analyzer(), fields.get("embedding").denseVector().dims(), fields.get("embedding").denseVector().similarity());
    }
}