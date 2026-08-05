package com.leo.careerforgeai.knowledge.infrastructure.document.loading;

import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * @program: CareerForge-AI
 * @description: 从配置白名单中安全读取 UTF-8 Markdown，并生成带原始 SHA-256 的 SourceDocument
 * @author: Miao Zheng
 * @date: 2026-07-31 15:22
 **/
@Component
@RequiredArgsConstructor
public class MarkdownDocumentLoader {

    private final KnowledgeSourceProperties properties;

    public List<SourceDocument> loadAll() {
        Path root = resolveRoot();
        return properties.getDocuments().stream()
                .map(definition -> loadDocument(root, definition))
                .toList();
    }

    private Path resolveRoot() {
        try {
            Path root = properties.getRootDirectory().toRealPath();
            if (!Files.isDirectory(root)) {
                throw new DocumentLoadException(
                        DocumentLoadErrorType.ROOT_NOT_DIRECTORY,
                        "知识文档根路径不是目录"
                );
            }
            return root;
        } catch (NoSuchFileException e) {
            throw new DocumentLoadException(
                    DocumentLoadErrorType.ROOT_NOT_FOUND,
                    "知识文档根目录不存在",
                    e
            );
        } catch (IOException e) {
            throw new DocumentLoadException(DocumentLoadErrorType.READ_FAILED, "知识文档根目录解析失败", e);
        }
    }

    private SourceDocument loadDocument(
            Path root,
            KnowledgeSourceProperties.DocumentDefinition definition
    ) {
        Path sourcePath = resolveSourcePath(root, definition.relativePath());
        byte[] rawBytes = readBytes(sourcePath, definition.relativePath());
        String sourceHash = calculateSha256(rawBytes);
        String rawContent = decodeUtf8(rawBytes, definition.relativePath());

        if (rawContent.isBlank()) {
            throw new DocumentLoadException(
                    DocumentLoadErrorType.EMPTY_DOCUMENT,
                    "知识文档内容为空：" + definition.relativePath()
            );
        }

        String relativeSourcePath = root.relativize(sourcePath)
                .toString()
                .replace(File.separatorChar, '/');

        return new SourceDocument(
                properties.getKnowledgeBaseId(),
                definition.documentId(),
                definition.documentName(),
                definition.documentType(),
                relativeSourcePath,
                sourceHash,
                rawContent
        );
    }

    private Path resolveSourcePath(Path root, String configuredPath) {
        Path relativePath;
        try {
            relativePath = Path.of(configuredPath);
        } catch (InvalidPathException e) {
            throw new DocumentLoadException(
                    DocumentLoadErrorType.INVALID_PATH,
                    "知识文档路径格式不合法",
                    e
            );
        }

        Path normalizedPath = root.resolve(relativePath).normalize();
        if (relativePath.isAbsolute() || !normalizedPath.startsWith(root)) {
            throw new DocumentLoadException(
                    DocumentLoadErrorType.PATH_OUTSIDE_ROOT,
                    "知识文档路径越过配置根目录"
            );
        }

        String fileName = normalizedPath.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new DocumentLoadException(
                    DocumentLoadErrorType.UNSUPPORTED_FILE_TYPE,
                    "只允许读取 Markdown 文件：" + configuredPath
            );
        }

        try {
            Path realPath = normalizedPath.toRealPath();
            if (!realPath.startsWith(root)) {
                throw new DocumentLoadException(
                        DocumentLoadErrorType.PATH_OUTSIDE_ROOT,
                        "知识文档真实路径越过配置根目录"
                );
            }
            if (!Files.isRegularFile(realPath) || !Files.isReadable(realPath)) {
                throw new DocumentLoadException(
                        DocumentLoadErrorType.FILE_NOT_READABLE,
                        "知识文档不是可读取的普通文件：" + configuredPath
                );
            }
            return realPath;
        } catch (NoSuchFileException e) {
            throw new DocumentLoadException(
                    DocumentLoadErrorType.FILE_NOT_FOUND,
                    "知识文档不存在：" + configuredPath,
                    e
            );
        } catch (IOException e) {
            throw new DocumentLoadException(
                    DocumentLoadErrorType.READ_FAILED,
                    "知识文档路径解析失败：" + configuredPath,
                    e
            );
        }
    }

    private byte[] readBytes(Path path, String configuredPath) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new DocumentLoadException(
                    DocumentLoadErrorType.READ_FAILED,
                    "知识文档读取失败：" + configuredPath,
                    e
            );
        }
    }

    private String decodeUtf8(byte[] rawBytes, String configuredPath) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawBytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new DocumentLoadException(
                    DocumentLoadErrorType.INVALID_UTF8,
                    "知识文档不是有效 UTF-8：" + configuredPath,
                    e
            );
        }
    }

    private String calculateSha256(byte[] rawBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}