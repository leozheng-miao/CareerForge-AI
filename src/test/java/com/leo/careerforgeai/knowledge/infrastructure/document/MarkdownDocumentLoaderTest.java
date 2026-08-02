package com.leo.careerforgeai.knowledge.infrastructure.document;

import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * @program: CareerForge-AI
 * @description: 验证文档加载的数据完整性、安全边界和结构化失败类型，不依赖真实 Elasticsearch、Ollama 或外部知识文档。
 * @author: Miao Zheng
 * @date: 2026-07-31 15:28
 **/
class MarkdownDocumentLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldLoadConfiguredMarkdownWithRawContentHashAndMetadata() throws Exception {
        byte[] rawBytes = "# 岗位要求\r\n熟悉 Java 和 RAG。".getBytes(StandardCharsets.UTF_8);
        Files.write(tempDir.resolve("materials.md"), rawBytes);
        Files.writeString(tempDir.resolve("unlisted.md"), "# 不应加载", StandardCharsets.UTF_8);

        List<SourceDocument> documents = createLoader(tempDir, "materials.md").loadAll();

        assertThat(documents).hasSize(1);
        SourceDocument document = documents.getFirst();
        assertThat(document.documentId()).isEqualTo("document-1");
        assertThat(document.documentName()).isEqualTo("materials");
        assertThat(document.documentType()).isEqualTo(KnowledgeDocumentType.JOB_DESCRIPTION);
        assertThat(document.sourcePath()).isEqualTo("materials.md");
        assertThat(document.sourceHash()).isEqualTo(sha256(rawBytes));
        assertThat(document.rawContent()).isEqualTo("# 岗位要求\r\n熟悉 Java 和 RAG。");
    }

    @Test
    void shouldRejectMissingRootDirectory() {
        assertLoadFails(createLoader(tempDir.resolve("missing-root"), "materials.md"), DocumentLoadErrorType.ROOT_NOT_FOUND);
    }

    @Test
    void shouldRejectRootThatIsNotDirectory() throws Exception {
        Path fileRoot = Files.writeString(tempDir.resolve("root.md"), "# 内容", StandardCharsets.UTF_8);
        assertLoadFails(createLoader(fileRoot, "materials.md"), DocumentLoadErrorType.ROOT_NOT_DIRECTORY);
    }

    @Test
    void shouldRejectUnsupportedFileType() throws Exception {
        Files.writeString(tempDir.resolve("materials.txt"), "文本", StandardCharsets.UTF_8);
        assertLoadFails(createLoader(tempDir, "materials.txt"), DocumentLoadErrorType.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void shouldRejectMissingDocument() {
        assertLoadFails(createLoader(tempDir, "missing.md"), DocumentLoadErrorType.FILE_NOT_FOUND);
    }

    @Test
    void shouldRejectPathTraversalOutsideRoot() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Files.writeString(tempDir.resolve("outside.md"), "# 外部文件", StandardCharsets.UTF_8);
        assertLoadFails(createLoader(root, "../outside.md"), DocumentLoadErrorType.PATH_OUTSIDE_ROOT);
    }

    @Test
    void shouldRejectSymbolicLinkOutsideRoot() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path outside = Files.writeString(tempDir.resolve("outside.md"), "# 外部文件", StandardCharsets.UTF_8);
        Files.createSymbolicLink(root.resolve("linked.md"), outside);
        assertLoadFails(createLoader(root, "linked.md"), DocumentLoadErrorType.PATH_OUTSIDE_ROOT);
    }

    @Test
    void shouldRejectInvalidUtf8() throws Exception {
        Files.write(tempDir.resolve("materials.md"), new byte[]{(byte) 0xC3, (byte) 0x28});
        assertLoadFails(createLoader(tempDir, "materials.md"), DocumentLoadErrorType.INVALID_UTF8);
    }

    @Test
    void shouldRejectBlankDocument() throws Exception {
        Files.writeString(tempDir.resolve("materials.md"), " \n\t", StandardCharsets.UTF_8);
        assertLoadFails(createLoader(tempDir, "materials.md"), DocumentLoadErrorType.EMPTY_DOCUMENT);
    }

    private MarkdownDocumentLoader createLoader(Path root, String relativePath) {
        var definition = new KnowledgeSourceProperties.DocumentDefinition("document-1", "materials", KnowledgeDocumentType.JOB_DESCRIPTION, relativePath);
        var properties = new KnowledgeSourceProperties("knowledge-base-1", root, List.of(definition));
        return new MarkdownDocumentLoader(properties);
    }

    private void assertLoadFails(MarkdownDocumentLoader loader, DocumentLoadErrorType expectedType) {
        assertThatThrownBy(loader::loadAll).isInstanceOfSatisfying(DocumentLoadException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(expectedType));
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}