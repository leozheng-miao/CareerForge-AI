package com.leo.careerforgeai.knowledge.infrastructure.rerank;

import com.leo.careerforgeai.knowledge.application.context.ContextAssembler;
import com.leo.careerforgeai.knowledge.application.rerank.KnowledgeRerankingService;
import com.leo.careerforgeai.knowledge.application.retrieval.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankStatus;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用真实 Elasticsearch、Ollama 和 DeepSeek 验证受限 LLM Rerank
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
@SpringBootTest
class DeepSeekLlmChunkRerankerSmoke {

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private KnowledgeRerankingService rerankingService;

    @Autowired
    private KnowledgeSourceProperties sourceProperties;

    @Autowired
    private ContextAssembler contextAssembler;

    /** 验证真实模型只能重新排列本次 RRF 候选。 */
    @Test
    void shouldRerankOnlyRetrievedCandidates() {
        String query = "Java 高并发场景中 CAS、Atomic 类和线程安全分别如何处理？";
        RetrievalScope scope = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of());
        HybridRetrievalResult retrievalResult = retrievalService.retrieveHybrid(query, scope, 10, 50, 5);
        List<RrfRankedChunk> rrfCandidates = retrievalResult.rrfChunks();

        assertThat(rrfCandidates).isNotEmpty();

        RerankedRetrievalResult rerankResult = rerankingService.rerank(query, retrievalResult, true);
        assertThat(rerankResult.status()).isEqualTo(RerankStatus.APPLIED);
        assertThat(rerankResult.rerankModel()).isNotBlank();
        assertThat(rerankResult.rerankInputTokens()).isPositive();
        assertThat(rerankResult.rerankOutputTokens()).isPositive();
        assertThat(rerankResult.rerankTotalTokens()).isEqualTo(rerankResult.rerankInputTokens() + rerankResult.rerankOutputTokens());

        List<RrfRankedChunk> rerankedCandidates = rerankResult.rankedChunks();

        AssembledContext context = contextAssembler.assemble(rerankedCandidates, 3_000);

        assertThat(context.chunks()).isNotEmpty();
        assertThat(context.usedContentChars()).isLessThanOrEqualTo(context.maxContentChars());

        Set<String> allowedChunkIds = rerankedCandidates.stream()
                .map(candidate -> candidate.chunk().chunkId())
                .collect(Collectors.toSet());
        assertThat(context.chunks()).allSatisfy(chunk -> assertThat(allowedChunkIds).contains(chunk.chunkId()));

        System.out.printf("contextSelected=%d, usedContentChars=%d, maxContentChars=%d, duplicateSkipped=%d, budgetSkipped=%d, assemblerVersion=%s%n", context.chunks().size(), context.usedContentChars(), context.maxContentChars(), context.duplicateSkippedCount(), context.budgetSkippedCount(), context.assemblerVersion());
        context.chunks().forEach(chunk -> System.out.printf("  contextChunkId=%s, documentId=%s, sectionPath=%s, chars=%d%n", chunk.chunkId().substring(0, 12), chunk.documentId(), chunk.sectionPath(), chunk.retrievalText().length()));

        assertThat(rerankedCandidates).hasSameSizeAs(rrfCandidates);
        assertThat(rerankedCandidates).extracting(item -> item.chunk().chunkId()).doesNotHaveDuplicates();
        assertThat(rerankedCandidates).extracting(item -> item.chunk().chunkId())
                .containsExactlyInAnyOrderElementsOf(rrfCandidates.stream().map(item -> item.chunk().chunkId()).toList());

        Map<String, RrfRankedChunk> candidateById = rrfCandidates.stream()
                .collect(Collectors.toMap(item -> item.chunk().chunkId(), item -> item));
        rerankedCandidates.forEach(candidate -> assertThat(candidate).isSameAs(candidateById.get(candidate.chunk().chunkId())));

        System.out.printf(
                "rerankStatus=%s, rerankModel=%s, rerankDurationMs=%d, inputTokens=%d, outputTokens=%d, totalTokens=%d%n",
                rerankResult.status(),
                rerankResult.rerankModel(),
                rerankResult.rerankDurationMs(),
                rerankResult.rerankInputTokens(),
                rerankResult.rerankOutputTokens(),
                rerankResult.rerankTotalTokens()
        );
        System.out.printf("query=%s, candidateCount=%d%n", query, rrfCandidates.size());
        System.out.println("beforeRerank:");
        rrfCandidates.forEach(candidate -> System.out.printf("  rrfRank=%d, chunkId=%s, sectionPath=%s%n", candidate.finalRank(), candidate.chunk().chunkId().substring(0, 12), candidate.chunk().sectionPath()));
        System.out.println("afterRerank:");
        IntStream.range(0, rerankedCandidates.size()).forEach(index -> {
            RrfRankedChunk candidate = rerankedCandidates.get(index);
            System.out.printf("  rerankRank=%d, originalRrfRank=%d, chunkId=%s, sectionPath=%s%n", index + 1, candidate.finalRank(), candidate.chunk().chunkId().substring(0, 12), candidate.chunk().sectionPath());
        });
    }
}