package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.indexing;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesResponse;
import com.leo.careerforgeai.knowledge.application.indexing.KnowledgeIndex;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.indexing.KnowledgeIndexFailure;
import com.leo.careerforgeai.knowledge.domain.indexing.KnowledgeIndexResult;
import com.leo.careerforgeai.knowledge.config.KnowledgeIndexProperties;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-03 14:05
 **/
@Component
@Slf4j
public class ElasticsearchKnowledgeIndex implements KnowledgeIndex {

    private final ElasticsearchClient client;
    private final KnowledgeIndexProperties properties;

    public ElasticsearchKnowledgeIndex(ElasticsearchClient client, KnowledgeIndexProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public KnowledgeIndexResult index(List<DocumentChunk> chunks, EmbeddingResult embeddingResult) {
        validateInput(chunks, embeddingResult);

        BulkRequest.Builder request = new BulkRequest.Builder()
                .index(properties.concreteIndexName())
                .refresh(Refresh.WaitFor);

        for (int index = 0; index < chunks.size(); index++) {
            DocumentChunk chunk = chunks.get(index);
            KnowledgeChunkIndexDocument document = KnowledgeChunkIndexDocument.from(chunk, embeddingResult.model(), embeddingResult.dimensions(), embeddingResult.vectors().get(index));
            request.operations(operation -> operation.index(item -> item.id(chunk.chunkId()).document(document)));
        }

        long startNanos = System.nanoTime();
        try {
            BulkResponse response = client.bulk(request.build());
            KnowledgeIndexResult result = mapResult(chunks, response);
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Elasticsearch Bulk入库完成，index={}, requested={}, indexed={}, failed={}, durationMs={}, serverTookMs={}", properties.concreteIndexName(), result.requestedCount(), result.indexedCount(), result.failedCount(), durationMs, response.took());
            return result;
        } catch (IOException | ElasticsearchException e) {
            throw new KnowledgeIndexException("Elasticsearch Bulk请求失败，index=" + properties.concreteIndexName(), e);
        }
    }

    @Override
    public void activateCurrentVersion() {
        String alias = properties.getIndexAlias();
        String targetIndex = properties.concreteIndexName();

        try {
            ElasticsearchIndicesClient indicesClient = client.indices();
            boolean aliasExists = indicesClient.existsAlias(request -> request.name(alias)).value();
            List<String> currentIndices = aliasExists
                    ? List.copyOf(indicesClient.getAlias(request -> request.name(alias)).aliases().keySet())
                    : List.of();

            if (currentIndices.size() == 1 && currentIndices.contains(targetIndex)) return;

            UpdateAliasesRequest.Builder request = new UpdateAliasesRequest.Builder();
            currentIndices.forEach(index -> request.actions(action -> action.remove(remove -> remove.index(index).alias(alias).mustExist(true))));
            request.actions(action -> action.add(add -> add.index(targetIndex).alias(alias)));

            UpdateAliasesResponse response = indicesClient.updateAliases(request.build());
            if (!response.acknowledged()) throw new KnowledgeIndexException("Elasticsearch Alias切换未确认，alias=" + alias + ", targetIndex=" + targetIndex);

            log.info("Elasticsearch知识索引Alias切换完成，alias={}, previousIndices={}, targetIndex={}", alias, currentIndices, targetIndex);
        } catch (IOException | ElasticsearchException e) {
            throw new KnowledgeIndexException("Elasticsearch Alias切换失败，alias=" + alias + ", targetIndex=" + targetIndex, e);
        }
    }

    private void validateInput(List<DocumentChunk> chunks, EmbeddingResult embeddingResult) {
        if (chunks == null || chunks.isEmpty()) throw new IllegalArgumentException("chunks 不能为空");
        if (embeddingResult == null) throw new IllegalArgumentException("embeddingResult 不能为空");
        if (chunks.size() != embeddingResult.vectors().size()) throw new IllegalArgumentException("Chunk数量必须等于向量数量");
    }

    /** 将 Bulk 的逐项响应转换为可观察的成功数和失败明细。 */
    private KnowledgeIndexResult mapResult(List<DocumentChunk> chunks, BulkResponse response) {
        if (response == null) throw new KnowledgeIndexException("Elasticsearch Bulk响应为空");
        if (response.items().size() != chunks.size()) throw new KnowledgeIndexException("Elasticsearch Bulk响应项数量与请求数量不一致");

        List<KnowledgeIndexFailure> failures = new ArrayList<>();
        for (int index = 0; index < response.items().size(); index++) {
            BulkResponseItem item = response.items().get(index);
            if (item.status() >= 200 && item.status() < 300 && item.error() == null) continue;
            if (item.status() < 400 || item.status() > 599) throw new KnowledgeIndexException("Elasticsearch Bulk返回非法失败状态，status=" + item.status());

            ErrorCause error = item.error();
            String errorType = error == null || error.type() == null || error.type().isBlank() ? "unknown" : error.type();
            String reason = error == null || error.reason() == null || error.reason().isBlank() ? "Elasticsearch未返回失败原因" : error.reason();
            failures.add(new KnowledgeIndexFailure(chunks.get(index).chunkId(), item.status(), errorType, reason));
        }

        return new KnowledgeIndexResult(chunks.size(), chunks.size() - failures.size(), failures);
    }
}