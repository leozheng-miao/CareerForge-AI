package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import org.springframework.stereotype.Component;

/**
 * @program: CareerForge-AI
 * @description: 使用 Elasticsearch 9.4.2 Java Client 构造 Chunk 索引的严格 Mapping。
 * @author: Miao Zheng
 * @date: 2026-08-03 13:18
 **/
@Component
public class KnowledgeChunkIndexDefinition {

    private final KnowledgeIndexProperties properties;

    public KnowledgeChunkIndexDefinition(KnowledgeIndexProperties properties) {
        this.properties = properties;
    }

    /** 根据实际 Embedding 维度构造版本化知识索引请求。 */
    public CreateIndexRequest createRequest(int embeddingDimensions) {
        if (embeddingDimensions <= 0) throw new IllegalArgumentException("embeddingDimensions 必须大于 0");

        return CreateIndexRequest.of(index -> index
                .index(properties.concreteIndexName())
                .settings(settings -> settings.numberOfShards("1").numberOfReplicas("0"))
                .mappings(mapping -> mapping
                        .dynamic(DynamicMapping.Strict)
                        .properties("knowledgeBaseId", property -> property.keyword(keyword -> keyword))
                        .properties("documentId", property -> property.keyword(keyword -> keyword))
                        .properties("documentName", property -> property.keyword(keyword -> keyword))
                        .properties("documentType", property -> property.keyword(keyword -> keyword))
                        .properties("sourcePath", property -> property.keyword(keyword -> keyword))
                        .properties("sourceHash", property -> property.keyword(keyword -> keyword))
                        .properties("cleaningVersion", property -> property.keyword(keyword -> keyword))
                        .properties("chunkerVersion", property -> property.keyword(keyword -> keyword))
                        .properties("embeddingModel", property -> property.keyword(keyword -> keyword))
                        .properties("embeddingDimensions", property -> property.integer(integer -> integer))
                        .properties("chunkId", property -> property.keyword(keyword -> keyword))
                        .properties("chunkIndex", property -> property.integer(integer -> integer))
                        .properties("sectionPath", property -> property.keyword(keyword -> keyword))
                        .properties("startOffset", property -> property.integer(integer -> integer))
                        .properties("endOffset", property -> property.integer(integer -> integer))
                        .properties("content", property -> property.text(text -> text.index(false)))
                        .properties("retrievalText", property -> property.text(text -> text.analyzer("cjk").searchAnalyzer("cjk")))
                        .properties("embedding", property -> property.denseVector(vector -> vector.dims(embeddingDimensions).index(true).similarity(DenseVectorSimilarity.Cosine)))
                )
        );
    }
}