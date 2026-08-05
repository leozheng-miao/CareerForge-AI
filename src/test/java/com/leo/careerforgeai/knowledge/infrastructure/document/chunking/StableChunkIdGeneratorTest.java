package com.leo.careerforgeai.knowledge.infrastructure.document.chunking;

import com.leo.careerforgeai.knowledge.domain.document.CleanedDocument;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-31 16:58
 **/
class StableChunkIdGeneratorTest {

    private static final String CHUNKER_VERSION = "markdown-structure-v1|max=1000|overlap=120";
    private final StableChunkIdGenerator generator = new StableChunkIdGenerator();

    @Test
    void shouldGenerateStableExpectedSha256() {
        String chunkId = generator.generate(document("knowledge-base-1", "测试材料", "materials.md", "0".repeat(64), "markdown-cleaner-v1"), CHUNKER_VERSION, 0);

        assertThat(chunkId).isEqualTo("22a130297c483225aaee4710ea5a2809bdda911a23836f2dc82cb3d827f9c60c");
    }

    @Test
    void shouldChangeIdWhenIdentityInputChanges() {
        CleanedDocument document = document("knowledge-base-1", "测试材料", "materials.md", "0".repeat(64), "markdown-cleaner-v1");
        String original = generator.generate(document, CHUNKER_VERSION, 0);

        assertThat(generator.generate(document, CHUNKER_VERSION, 1)).isNotEqualTo(original);
        assertThat(generator.generate(document, "markdown-structure-v1|max=600|overlap=80", 0)).isNotEqualTo(original);
        assertThat(generator.generate(document("knowledge-base-1", "测试材料", "materials.md", "1".repeat(64), "markdown-cleaner-v1"), CHUNKER_VERSION, 0)).isNotEqualTo(original);
        assertThat(generator.generate(document("knowledge-base-1", "测试材料", "materials.md", "0".repeat(64), "markdown-cleaner-v2"), CHUNKER_VERSION, 0)).isNotEqualTo(original);
        assertThat(generator.generate(document("knowledge-base-2", "测试材料", "materials.md", "0".repeat(64), "markdown-cleaner-v1"), CHUNKER_VERSION, 0)).isNotEqualTo(original);
    }

    @Test
    void shouldKeepIdWhenOnlyDisplayMetadataChanges() {
        CleanedDocument original = document("knowledge-base-1", "原名称", "old-path.md", "0".repeat(64), "markdown-cleaner-v1");
        CleanedDocument renamed = document("knowledge-base-1", "新名称", "new-path.md", "0".repeat(64), "markdown-cleaner-v1");

        assertThat(generator.generate(renamed, CHUNKER_VERSION, 0)).isEqualTo(generator.generate(original, CHUNKER_VERSION, 0));
    }

    @Test
    void shouldRejectInvalidArguments() {
        CleanedDocument document = document("knowledge-base-1", "测试材料", "materials.md", "0".repeat(64), "markdown-cleaner-v1");

        assertThatThrownBy(() -> generator.generate(null, CHUNKER_VERSION, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(document, " ", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(document, CHUNKER_VERSION, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private CleanedDocument document(String knowledgeBaseId, String documentName, String sourcePath, String sourceHash, String cleaningVersion) {
        SourceDocument source = new SourceDocument(knowledgeBaseId, "document-1", documentName, KnowledgeDocumentType.JOB_DESCRIPTION, sourcePath, sourceHash, "正文");
        return new CleanedDocument(source, cleaningVersion, "正文");
    }
}