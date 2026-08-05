package com.leo.careerforgeai.knowledge.application.answer;

import com.leo.careerforgeai.knowledge.application.context.ContextAssembler;
import com.leo.careerforgeai.knowledge.application.rerank.KnowledgeRerankingService;
import com.leo.careerforgeai.knowledge.application.retrieval.KnowledgeRetrievalService;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswer;
import com.leo.careerforgeai.knowledge.domain.answer.RagAnswerStatus;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankStatus;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用真实 Elasticsearch、Ollama 和 DeepSeek 验证带 Java 引用校验的完整 RAG 回答链路
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
@SpringBootTest
class RagAnswerSmoke {

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private KnowledgeRerankingService rerankingService;

    @Autowired
    private ContextAssembler contextAssembler;

    @Autowired
    private RagAnswerService answerService;

    @Autowired
    private KnowledgeSourceProperties sourceProperties;

    /** 验证真实回答的所有引用都来自本次组装上下文。 */
    @Test
    void shouldGenerateAnswerWithValidatedCitations() {
        String query = "Java 高并发场景中 CAS 和 Atomic 类分别适合解决什么问题？";
        RetrievalScope scope = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of());

        HybridRetrievalResult hybridResult = retrievalService.retrieveHybrid(query, scope, 10, 50, 5);
        RerankedRetrievalResult rerankedResult = rerankingService.rerank(query, hybridResult, true);
        assertThat(rerankedResult.status()).isIn(RerankStatus.APPLIED, RerankStatus.FALLBACK);

        AssembledContext context = contextAssembler.assemble(rerankedResult.rankedChunks(), 3_000);
        assertThat(context.chunks()).isNotEmpty();

        RagAnswer answer = answerService.answer(query, context);

        assertThat(answer.status()).isEqualTo(RagAnswerStatus.ANSWERED);
        assertThat(answer.citations()).isNotEmpty();

        Set<String> allowedChunkIds = context.chunks().stream().map(chunk -> chunk.chunkId()).collect(Collectors.toSet());
        assertThat(answer.citations()).allSatisfy(citation -> {
            assertThat(allowedChunkIds).contains(citation.chunkId());
            assertThat(citation.documentName()).isNotBlank();
            assertThat(citation.sectionPath()).isNotNull();
        });

        System.out.printf("query=%s%n", query);
        System.out.printf("rerankStatus=%s, contextChunks=%d, answerStatus=%s%n", rerankedResult.status(), context.chunks().size(), answer.status());
        System.out.printf("answer=%s%n", answer.answer());
        answer.citations().forEach(citation -> System.out.printf("citationChunkId=%s, documentName=%s, documentId=%s, sectionPath=%s, offsets=%d-%d, sourceHash=%s%n", citation.chunkId().substring(0, 12), citation.documentName(), citation.documentId(), citation.sectionPath(), citation.startOffset(), citation.endOffset(), citation.sourceHash()));
    }
}