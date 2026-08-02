package com.leo.careerforgeai.knowledge.infrastructure.document;

import com.leo.careerforgeai.knowledge.domain.CleanedDocument;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用真实配置验证两份固定知识材料的读取、哈希和清洗结果
 * @author: Miao Zheng
 * @date: 2026-07-31
 **/
@SpringBootTest
class MarkdownKnowledgeSmoke {

    private static final Map<String, String> EXPECTED_SOURCE_HASHES = Map.of(
            "ai-job-jd-summary", "dc6ccd74b0469e0a85173b5b14fd844e74e2db4f5899eb01497d4520ffcd7542",
            "ai-interview-summary", "1ae00e8fed0b9caf58ae3bb5d1fb2e195d8a1071bcb3651b6e89dcf9ecedf0e7"
    );

    @Autowired
    private MarkdownDocumentLoader loader;

    @Autowired
    private DocumentCleaner cleaner;

    @Test
    void shouldLoadAndCleanCurrentKnowledgeCorpus() {
        List<SourceDocument> sourceDocuments = loader.loadAll();
        List<CleanedDocument> cleanedDocuments = sourceDocuments.stream().map(cleaner::clean).toList();

        assertThat(sourceDocuments)
                .extracting(SourceDocument::documentId)
                .containsExactly("ai-job-jd-summary", "ai-interview-summary");

        assertThat(sourceDocuments).allSatisfy(document -> {
            assertThat(document.sourceHash()).isEqualTo(EXPECTED_SOURCE_HASHES.get(document.documentId()));
            assertThat(document.rawContent()).isNotBlank();
        });

        assertThat(cleanedDocuments).hasSameSizeAs(sourceDocuments);
        assertThat(cleanedDocuments).allSatisfy(document -> {
            assertThat(document.cleaningVersion()).isEqualTo(DocumentCleaner.CLEANING_VERSION);
            assertThat(document.cleanedContent()).isNotBlank().doesNotContain("\r").doesNotStartWith("\uFEFF");
        });

        cleanedDocuments.forEach(document -> System.out.printf(
                "documentId=%s, sourceHash=%s, rawChars=%d, cleanedChars=%d, cleaningVersion=%s%n",
                document.sourceDocument().documentId(),
                document.sourceDocument().sourceHash(),
                document.sourceDocument().rawContent().length(),
                document.cleanedContent().length(),
                document.cleaningVersion()
        ));
    }
}