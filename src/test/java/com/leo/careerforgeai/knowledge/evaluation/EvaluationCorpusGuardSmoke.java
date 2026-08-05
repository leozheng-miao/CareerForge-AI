package com.leo.careerforgeai.knowledge.evaluation;

import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import com.leo.careerforgeai.knowledge.infrastructure.document.ChunkingProperties;
import com.leo.careerforgeai.knowledge.infrastructure.document.DocumentCleaner;
import com.leo.careerforgeai.knowledge.infrastructure.document.MarkdownDocumentLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(properties = {
        "careerforge.model.base-url=https://example.invalid",
        "careerforge.model.api-key=smoke-test-key",
        "careerforge.model.name=smoke-test-model"
})
class EvaluationCorpusGuardSmoke {

    @Autowired
    private MarkdownDocumentLoader documentLoader;

    @Autowired
    private ChunkingProperties chunkingProperties;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void shouldMatchCurrentCorpusAndProcessingVersions() {
        EvaluationCorpusGuard guard = new EvaluationCorpusGuard(jsonMapper);
        EvaluationCorpusManifest manifest = guard.loadManifest();
        List<SourceDocument> documents = documentLoader.loadAll();

        assertThatCode(() -> guard.verify(manifest, documents, DocumentCleaner.CLEANING_VERSION, chunkingProperties.chunkerVersion())).doesNotThrowAnyException();

        System.out.printf(
                "schemaVersion=%s, documents=%d, cleaningVersion=%s, chunkerVersion=%s, corpusMatched=true%n",
                manifest.schemaVersion(),
                documents.size(),
                DocumentCleaner.CLEANING_VERSION,
                chunkingProperties.chunkerVersion()
        );
    }
}