package com.leo.careerforgeai.knowledge.evaluation;

import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationCorpusGuardTest {

    private static final String SOURCE_HASH = "a".repeat(64);
    private final EvaluationCorpusGuard guard = new EvaluationCorpusGuard(JsonMapper.builder().build());

    @Test
    void shouldLoadStrictManifestFromClasspath() {
        EvaluationCorpusManifest manifest = guard.loadManifest();

        assertThat(manifest.schemaVersion()).isEqualTo("rag-evaluation-corpus-v1");
        assertThat(manifest.cleaningVersion()).isEqualTo("markdown-cleaner-v2");
        assertThat(manifest.chunkerVersion()).isEqualTo("markdown-structure-v2|max=1000|overlap=120");
        assertThat(manifest.documents()).hasSize(2);
    }

    @Test
    void shouldAcceptMatchingCorpusSnapshot() {
        EvaluationCorpusManifest manifest = manifest();
        List<SourceDocument> documents = List.of(sourceDocument(SOURCE_HASH));

        assertThatCode(() -> guard.verify(manifest, documents, "markdown-cleaner-v2", "markdown-structure-v2|max=1000|overlap=120")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectCorpusOrProcessingVersionDrift() {
        EvaluationCorpusManifest manifest = manifest();

        assertThatThrownBy(() -> guard.verify(manifest, List.of(sourceDocument("b".repeat(64))), "markdown-cleaner-v2", "markdown-structure-v2|max=1000|overlap=120"))
                .isInstanceOf(CorpusDriftException.class)
                .hasMessageContaining("sourceHash 不一致");

        assertThatThrownBy(() -> guard.verify(manifest, List.of(sourceDocument(SOURCE_HASH)), "markdown-cleaner-v3", "markdown-structure-v2|max=1000|overlap=120"))
                .isInstanceOf(CorpusDriftException.class)
                .hasMessageContaining("cleaningVersion 不一致");

        assertThatThrownBy(() -> guard.verify(manifest, List.of(sourceDocument(SOURCE_HASH)), "markdown-cleaner-v2", "markdown-structure-v3|max=1000|overlap=120"))
                .isInstanceOf(CorpusDriftException.class)
                .hasMessageContaining("chunkerVersion 不一致");
    }

    private EvaluationCorpusManifest manifest() {
        return new EvaluationCorpusManifest(
                "rag-evaluation-corpus-v1",
                "careerforge",
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                List.of(new EvaluationCorpusManifest.DocumentSnapshot(
                        "document-1",
                        "测试文档.md",
                        KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                        "测试文档.md",
                        SOURCE_HASH
                ))
        );
    }

    private SourceDocument sourceDocument(String sourceHash) {
        return new SourceDocument(
                "careerforge",
                "document-1",
                "测试文档.md",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                "测试文档.md",
                sourceHash,
                "测试正文"
        );
    }
}