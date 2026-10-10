package com.leo.careerforgeai.interviewflow.infrastructure.document;

import com.leo.careerforgeai.interviewflow.application.resume.ExtractedResumeText;
import com.leo.careerforgeai.interviewflow.application.resume.ExtractedResumeText.DocumentType;
import com.leo.careerforgeai.interviewflow.application.resume.ExtractedResumeText.ExtractionMetadata;
import com.leo.careerforgeai.interviewflow.application.resume.ResumeTextExtractor;
import com.leo.careerforgeai.interviewflow.application.resume.ResumeUpload;
import com.leo.careerforgeai.interviewflow.config.ResumeParsingProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * 先限制上传与DOCX展开资源，再在有界时间内提取文本。
 * 临时文件仅存在于当次方法中，结果不暴露路径。
 */
@Component
public final class ResumeDocumentReader implements AutoCloseable {
    private static final String PDF_MIME = "application/pdf";
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int BUFFER_SIZE = 8_192;

    private final ResumeParsingProperties properties;
    private final Map<DocumentType, ResumeTextExtractor> extractors;
    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    public ResumeDocumentReader(
            ResumeParsingProperties properties,
            List<ResumeTextExtractor> extractors
    ) {
        this.properties = Objects.requireNonNull(properties, "properties不能为null");
        Objects.requireNonNull(extractors, "extractors不能为null");
        EnumMap<DocumentType, ResumeTextExtractor> indexed =
                new EnumMap<>(DocumentType.class);
        for (ResumeTextExtractor extractor : extractors) {
            Objects.requireNonNull(extractor, "extractor不能为null");
            if (indexed.putIfAbsent(extractor.documentType(), extractor) != null) {
                throw new IllegalStateException(
                        "同一文档类型只能注册一个提取器: "
                                + extractor.documentType()
                );
            }
        }
        for (DocumentType type : DocumentType.values()) {
            if (!indexed.containsKey(type)) {
                throw new IllegalStateException("缺少文本提取器: " + type);
            }
        }
        this.extractors = Map.copyOf(indexed);
    }

    public ExtractedResumeText extract(ResumeUpload upload) {
        Objects.requireNonNull(upload, "upload不能为null");
        byte[] content = upload.content();
        validateUploadSize(content);
        DocumentType type = validateDeclaredType(upload);
        validateSignature(type, content);

        Path requestDirectory = null;
        Path temporaryFile = null;
        try {
            Path baseDirectory = prepareBaseDirectory();
            requestDirectory = Files.createTempDirectory(baseDirectory, "request-");
            temporaryFile = requestDirectory.resolve(
                    type == DocumentType.PDF ? "resume.pdf" : "resume.docx"
            );
            Files.write(temporaryFile, content);
            if (type == DocumentType.DOCX) {
                validateDocxContainer(temporaryFile);
            }
            byte[] verifiedContent = Files.readAllBytes(temporaryFile);
            ResumeTextExtractor.Extraction extraction = extractWithTimeout(
                    extractors.get(type),
                    verifiedContent
            );
            String normalized = normalize(extraction.text());
            validateExtractedText(normalized);
            return new ExtractedResumeText(
                    normalized,
                    type,
                    new ExtractionMetadata(
                            content.length,
                            extraction.sourceUnits(),
                            normalized.length()
                    )
            );
        } catch (ResumeParsingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ResumeParsingException(
                    FailureReason.PARSE_FAILED,
                    "简历文档解析失败",
                    exception
            );
        } finally {
            cleanupExactPaths(temporaryFile, requestDirectory);
        }
    }

    private void validateUploadSize(byte[] content) {
        if (content.length == 0) {
            throw new ResumeParsingException(
                    FailureReason.EMPTY_UPLOAD,
                    "简历文件不能为空"
            );
        }
        if (content.length > properties.maxUploadSize().toBytes()) {
            throw new ResumeParsingException(
                    FailureReason.UPLOAD_TOO_LARGE,
                    "简历文件大小超过限制"
            );
        }
    }

    private static DocumentType validateDeclaredType(ResumeUpload upload) {
        String filename = upload.originalFilename().strip()
                .toLowerCase(Locale.ROOT);
        if (filename.endsWith(".pdf")) {
            if (!PDF_MIME.equalsIgnoreCase(upload.contentType())) {
                throw new ResumeParsingException(
                        FailureReason.MIME_MISMATCH,
                        "PDF扩展名与MIME类型不匹配"
                );
            }
            return DocumentType.PDF;
        }
        if (filename.endsWith(".docx")) {
            if (!DOCX_MIME.equalsIgnoreCase(upload.contentType())) {
                throw new ResumeParsingException(
                        FailureReason.MIME_MISMATCH,
                        "DOCX扩展名与MIME类型不匹配"
                );
            }
            return DocumentType.DOCX;
        }
        throw new ResumeParsingException(
                FailureReason.UNSUPPORTED_TYPE,
                "仅支持PDF或DOCX简历"
        );
    }

    private static void validateSignature(DocumentType type, byte[] content) {
        boolean valid = switch (type) {
            case PDF -> startsWith(content, new byte[]{'%', 'P', 'D', 'F', '-'});
            case DOCX -> startsWith(content, new byte[]{'P', 'K', 3, 4});
        };
        if (!valid) {
            throw new ResumeParsingException(
                    FailureReason.SIGNATURE_MISMATCH,
                    "文件签名与声明类型不匹配"
            );
        }
    }

    private Path prepareBaseDirectory() throws IOException {
        Path configured = properties.tempDirectory().toAbsolutePath().normalize();
        Files.createDirectories(configured);
        if (Files.isSymbolicLink(configured) || !Files.isDirectory(configured)) {
            throw new ResumeParsingException(
                    FailureReason.TEMP_STORAGE_INVALID,
                    "简历临时目录不安全"
            );
        }
        return configured.toRealPath();
    }

    private void validateDocxContainer(Path file) throws IOException {
        int entryCount = 0;
        long expandedBytes = 0;
        boolean contentTypesFound = false;
        boolean documentFound = false;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount = Math.addExact(entryCount, 1);
                if (entryCount > properties.maxDocxEntries()) {
                    throw new ResumeParsingException(
                            FailureReason.DOCX_ENTRY_LIMIT,
                            "DOCX压缩包条目数超过限制"
                    );
                }
                String name = validateEntryName(entry.getName());
                String lower = name.toLowerCase(Locale.ROOT);
                if ("[content_types].xml".equals(lower)) {
                    contentTypesFound = true;
                }
                if ("word/document.xml".equals(lower)) {
                    documentFound = true;
                }
                if (lower.endsWith("vbaproject.bin")) {
                    throw new ResumeParsingException(
                            FailureReason.DOCX_UNSAFE_CONTENT,
                            "DOCX不允许宏内容"
                    );
                }
                ByteArrayOutputStream relationshipContent = lower.endsWith(".rels")
                        ? new ByteArrayOutputStream()
                        : null;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes = Math.addExact(expandedBytes, read);
                    if (expandedBytes
                            > properties.maxDocxExpandedSize().toBytes()) {
                        throw new ResumeParsingException(
                                FailureReason.DOCX_EXPANDED_SIZE_LIMIT,
                                "DOCX压缩包展开大小超过限制"
                        );
                    }
                    if (relationshipContent != null) {
                        relationshipContent.write(buffer, 0, read);
                    }
                }
                if (relationshipContent != null) {
                    String relationships = relationshipContent.toString(
                            StandardCharsets.UTF_8);
                    if (relationships.matches(
                            "(?is).*TargetMode\\s*=\\s*['\"]External['\"].*")) {
                        throw new ResumeParsingException(
                                FailureReason.DOCX_UNSAFE_CONTENT,
                                "DOCX不允许外部资源关系"
                        );
                    }
                }
                zip.closeEntry();
            }
        } catch (ZipException exception) {
            throw new ResumeParsingException(
                    FailureReason.MALFORMED_DOCUMENT,
                    "DOCX压缩包已损坏",
                    exception
            );
        }
        if (!contentTypesFound || !documentFound) {
            throw new ResumeParsingException(
                    FailureReason.MALFORMED_DOCUMENT,
                    "DOCX缺少必要文档结构"
            );
        }
    }

    private ResumeTextExtractor.Extraction extractWithTimeout(
            ResumeTextExtractor extractor,
            byte[] content
    ) {
        Future<ResumeTextExtractor.Extraction> future = executor.submit(
                () -> extractor.extract(content)
        );
        try {
            return future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ResumeParsingException(
                    FailureReason.TIMEOUT,
                    "简历文档解析超时",
                    exception
            );
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ResumeParsingException(
                    FailureReason.INTERRUPTED,
                    "简历文档解析被中断",
                    exception
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ResumeParsingException parsingException) {
                throw parsingException;
            }
            throw new ResumeParsingException(
                    FailureReason.PARSE_FAILED,
                    "简历文档解析失败",
                    cause
            );
        }
    }

    private void validateExtractedText(String text) {
        if (text.isBlank()) {
            throw new ResumeParsingException(
                    FailureReason.EMPTY_TEXT,
                    "未从简历中提取到文本，扫描件暂不支持"
            );
        }
        if (text.length() > properties.maxTextCharacters()) {
            throw new ResumeParsingException(
                    FailureReason.TEXT_TOO_LONG,
                    "简历文本长度超过限制"
            );
        }
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
        String[] lines = normalized.split("\\n", -1);
        StringBuilder output = new StringBuilder();
        int blankLines = 0;
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                blankLines++;
                continue;
            }
            if (!output.isEmpty()) {
                output.append(blankLines > 0 ? "\n\n" : "\n");
            }
            output.append(stripped.replace('\t', ' '));
            blankLines = 0;
        }
        return output.toString();
    }

    private static String validateEntryName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\\') >= 0) {
            throw unsafeEntry();
        }
        try {
            Path path = Path.of(name).normalize();
            if (path.isAbsolute() || path.startsWith("..")) {
                throw unsafeEntry();
            }
            return path.toString().replace('\\', '/');
        } catch (InvalidPathException exception) {
            throw new ResumeParsingException(
                    FailureReason.DOCX_UNSAFE_CONTENT,
                    "DOCX包含不安全路径",
                    exception
            );
        }
    }

    private static ResumeParsingException unsafeEntry() {
        return new ResumeParsingException(
                FailureReason.DOCX_UNSAFE_CONTENT,
                "DOCX包含不安全路径"
        );
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static void cleanupExactPaths(
            Path file,
            Path requestDirectory
    ) {
        IOException failure = null;
        for (Path path : new Path[]{file, requestDirectory}) {
            if (path == null) {
                continue;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw new ResumeParsingException(
                    FailureReason.TEMP_CLEANUP_FAILED,
                    "简历临时数据清理失败",
                    failure
            );
        }
    }

    @Override
    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    public enum FailureReason {
        EMPTY_UPLOAD,
        UPLOAD_TOO_LARGE,
        UNSUPPORTED_TYPE,
        MIME_MISMATCH,
        SIGNATURE_MISMATCH,
        ENCRYPTED_PDF,
        MALFORMED_DOCUMENT,
        DOCX_ENTRY_LIMIT,
        DOCX_EXPANDED_SIZE_LIMIT,
        DOCX_UNSAFE_CONTENT,
        TEXT_TOO_LONG,
        EMPTY_TEXT,
        TIMEOUT,
        INTERRUPTED,
        TEMP_STORAGE_INVALID,
        TEMP_CLEANUP_FAILED,
        PARSE_FAILED
    }

    public static final class ResumeParsingException
            extends RuntimeException {
        private final FailureReason reason;

        public ResumeParsingException(FailureReason reason, String message) {
            super(message);
            this.reason = Objects.requireNonNull(reason, "reason不能为null");
        }

        public ResumeParsingException(
                FailureReason reason,
                String message,
                Throwable cause
        ) {
            super(message, cause);
            this.reason = Objects.requireNonNull(reason, "reason不能为null");
        }

        public FailureReason reason() {
            return reason;
        }
    }
}
