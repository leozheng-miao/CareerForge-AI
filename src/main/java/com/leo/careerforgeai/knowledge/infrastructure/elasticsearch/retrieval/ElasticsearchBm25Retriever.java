package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.leo.careerforgeai.knowledge.application.retrieval.Bm25Retriever;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.config.KnowledgeIndexProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 通过 Alias、match 查询和元数据过滤执行真实 BM25 检索
 * @author: Miao Zheng
 * @date: 2026-08-03 22:24
 **/
@Component
@Slf4j
public class ElasticsearchBm25Retriever implements Bm25Retriever {

    private static final int MAX_TOP_K = 100;
    private static final List<String> SOURCE_FIELDS = List.of(
            "knowledgeBaseId", "documentId", "documentName", "documentType",
            "sourcePath", "sourceHash", "cleaningVersion", "chunkerVersion",
            "chunkId", "chunkIndex", "sectionPath", "startOffset", "endOffset", "content"
    );

    private final ElasticsearchClient client;
    private final KnowledgeIndexProperties properties;

    public ElasticsearchBm25Retriever(ElasticsearchClient client, KnowledgeIndexProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public RetrievalResult retrieve(String query, RetrievalScope scope, int topK) {
        validateInput(query, scope, topK);
        SearchRequest request = buildRequest(query, scope, topK);
        long startNanos = System.nanoTime();

        try {
            SearchResponse<KnowledgeChunkSearchDocument> response = client.search(request, KnowledgeChunkSearchDocument.class);
            if (response.timedOut()) throw new KnowledgeRetrievalException("Elasticsearch BM25查询超时");

            RetrievalResult result = mapResult(response, Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
            log.info("Elasticsearch BM25检索完成，alias={}, topK={}, hits={}, durationMs={}, serverTookMs={}", properties.getIndexAlias(), topK, result.chunks().size(), result.durationMs(), response.took());
            return result;
        } catch (IOException | ElasticsearchException e) {
            throw new KnowledgeRetrievalException("Elasticsearch BM25查询失败，alias=" + properties.getIndexAlias(), e);
        }
    }

    private SearchRequest buildRequest(String query, RetrievalScope scope, int topK) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder()
                .must(clause -> clause.match(match -> match.field("retrievalText").query(query)))
                .filter(clause -> clause.term(term -> term.field("knowledgeBaseId").value(scope.knowledgeBaseId())));

        if (!scope.documentTypes().isEmpty()) {
            List<FieldValue> documentTypes = scope.documentTypes().stream()
                    .map(Enum::name)
                    .sorted()
                    .map(FieldValue::of)
                    .toList();
            boolQuery.filter(clause -> clause.terms(terms -> terms.field("documentType").terms(values -> values.value(documentTypes))));
        }

        if (!scope.documentIds().isEmpty()) {
            List<FieldValue> documentIds = scope.documentIds().stream()
                    .sorted()
                    .map(FieldValue::of)
                    .toList();
            boolQuery.filter(clause -> clause.terms(terms -> terms.field("documentId").terms(values -> values.value(documentIds))));
        }

        return new SearchRequest.Builder()
                .index(properties.getIndexAlias())
                .size(topK)
                .allowPartialSearchResults(false)
                .source(source -> source.filter(filter -> filter.includes(SOURCE_FIELDS).excludeVectors(true)))
                .query(boolQuery.build()._toQuery())
                .build();
    }

    private RetrievalResult mapResult(SearchResponse<KnowledgeChunkSearchDocument> response, long durationMs) {
        List<RetrievedChunk> retrievedChunks = new ArrayList<>();
        List<Hit<KnowledgeChunkSearchDocument>> hits = response.hits().hits();

        for (int index = 0; index < hits.size(); index++) {
            Hit<KnowledgeChunkSearchDocument> hit = hits.get(index);
            KnowledgeChunkSearchDocument source = hit.source();
            if (source == null) throw new KnowledgeRetrievalException("Elasticsearch BM25命中缺少_source");
            if (!hit.id().equals(source.chunkId())) throw new KnowledgeRetrievalException("Elasticsearch _id与chunkId不一致");
            if (hit.score() == null || !Double.isFinite(hit.score())) throw new KnowledgeRetrievalException("Elasticsearch BM25命中缺少有效分数");

            DocumentChunk chunk;
            try {
                chunk = source.toDomain();
            } catch (IllegalArgumentException e) {
                throw new KnowledgeRetrievalException("Elasticsearch BM25命中包含非法Chunk数据，chunkId=" + source.chunkId(), e);
            }

            retrievedChunks.add(new RetrievedChunk(chunk, hit.score(), index + 1));
        }

        return new RetrievalResult(retrievedChunks, durationMs);
    }

    private void validateInput(String query, RetrievalScope scope, int topK) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        if (scope == null) throw new IllegalArgumentException("scope 不能为空");
        if (topK <= 0 || topK > MAX_TOP_K) throw new IllegalArgumentException("topK 必须在 1 到 " + MAX_TOP_K + " 之间");
    }
}