package com.leo.careerforgeai.interviewflow.infrastructure.document;

import com.leo.careerforgeai.interviewflow.application.resume.ExtractedResumeText;
import com.leo.careerforgeai.interviewflow.application.resume.ExtractedResumeText.DocumentType;
import com.leo.careerforgeai.interviewflow.application.resume.ResumeTextExtractor;
import com.leo.careerforgeai.interviewflow.application.resume.ResumeUpload;
import com.leo.careerforgeai.interviewflow.config.ResumeParsingProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeParsingTest {

    private static final String PDF_MIME = "application/pdf";
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @TempDir
    Path tempDirectory;

    @Test
    void extractsAndNormalizesValidPdfAndDocxWithoutReturningAFilePath()
            throws Exception {
        try (ResumeDocumentReader reader = reader(properties())) {
            ExtractedResumeText pdf = reader.extract(new ResumeUpload(
                    "resume.pdf", PDF_MIME, pdf("Java Agent experience")));
            ExtractedResumeText docx = reader.extract(new ResumeUpload(
                    "resume.docx", DOCX_MIME,
                    docx("  Spring Boot\r\n\r\n\r\nAgent workflow  ")));

            assertThat(pdf.documentType()).isEqualTo(DocumentType.PDF);
            assertThat(pdf.text()).contains("Java Agent experience");
            assertThat(pdf.metadata().sourceUnits()).isEqualTo(1);
            assertThat(docx.documentType()).isEqualTo(DocumentType.DOCX);
            assertThat(docx.text()).isEqualTo("Spring Boot\n\nAgent workflow");
            assertThat(docx.metadata().extractedCharacters())
                    .isEqualTo(docx.text().length());
        }
        assertTempDirectoryEmpty();
    }

    @Test
    void rejectsUnsupportedExtensionMimeAndSignatureMismatch() throws Exception {
        try (ResumeDocumentReader reader = reader(properties())) {
            assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                    "resume.doc", "application/msword", new byte[]{1, 2, 3})))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("PDF或DOCX");
            assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                    "resume.png", "image/png",
                    new byte[]{(byte) 0x89, 'P', 'N', 'G'})))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("PDF或DOCX");
            assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                    "resume.pdf", DOCX_MIME, pdf("text"))))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("MIME");
            assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                    "resume.pdf", PDF_MIME, docx("text"))))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("签名");
        }
        assertTempDirectoryEmpty();
    }

    @Test
    void rejectsEncryptedPdfBeforeTextExtraction() throws Exception {
        try (ResumeDocumentReader reader = reader(properties())) {
            assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                    "encrypted.pdf", PDF_MIME, encryptedPdf())))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("加密PDF");
        }
        assertTempDirectoryEmpty();
    }

    @Test
    void rejectsMalformedOrStructurallyUnsafeDocx() throws Exception {
        byte[] malformed = new byte[]{'P', 'K', 3, 4, 1, 2, 3};
        byte[] traversal = rawDocx(List.of(
                entry("[Content_Types].xml", "types"),
                entry("word/document.xml", "document"),
                entry("../outside.xml", "escape")
        ));
        byte[] macro = rawDocx(List.of(
                entry("[Content_Types].xml", "types"),
                entry("word/document.xml", "document"),
                entry("word/vbaProject.bin", "macro")
        ));
        byte[] externalRelationship = rawDocx(List.of(
                entry("[Content_Types].xml", "types"),
                entry("word/document.xml", "document"),
                entry("word/_rels/document.xml.rels",
                        "<Relationship TargetMode=\"External\" Target=\"https://example.com\"/>")
        ));

        try (ResumeDocumentReader reader = reader(properties())) {
            for (byte[] content : List.of(
                    malformed, traversal, macro, externalRelationship)) {
                assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                        "resume.docx", DOCX_MIME, content)))
                        .isInstanceOf(
                                ResumeDocumentReader.ResumeParsingException.class);
            }
        }
        assertTempDirectoryEmpty();
    }

    @Test
    void rejectsDocxEntryCountAndExpandedSizeBeforePoiConstruction()
            throws Exception {
        byte[] tooManyEntries = rawDocx(List.of(
                entry("[Content_Types].xml", "types"),
                entry("word/document.xml", "document"),
                entry("word/a.xml", "a"),
                entry("word/b.xml", "b")
        ));
        byte[] expanded = rawDocx(List.of(
                entry("[Content_Types].xml", "types"),
                entry("word/document.xml", "x".repeat(10_000))
        ));

        try (ResumeDocumentReader entryLimited = reader(new ResumeParsingProperties(
                tempDirectory, DataSize.ofMegabytes(1), 3,
                DataSize.ofMegabytes(2), 10_000, Duration.ofSeconds(2)))) {
            assertThatThrownBy(() -> entryLimited.extract(new ResumeUpload(
                    "resume.docx", DOCX_MIME, tooManyEntries)))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("条目");
        }
        try (ResumeDocumentReader sizeLimited = reader(new ResumeParsingProperties(
                tempDirectory, DataSize.ofMegabytes(1), 20,
                DataSize.ofBytes(1_000), 10_000, Duration.ofSeconds(2)))) {
            assertThatThrownBy(() -> sizeLimited.extract(new ResumeUpload(
                    "resume.docx", DOCX_MIME, expanded)))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("展开");
        }
        assertTempDirectoryEmpty();
    }

    @Test
    void rejectsUploadAndExtractedTextOverConfiguredLimits() throws Exception {
        ResumeParsingProperties uploadLimited = new ResumeParsingProperties(
                tempDirectory, DataSize.ofBytes(16), 100,
                DataSize.ofMegabytes(2), 10_000, Duration.ofSeconds(2));
        ResumeParsingProperties textLimited = new ResumeParsingProperties(
                tempDirectory, DataSize.ofMegabytes(1), 100,
                DataSize.ofMegabytes(2), 10, Duration.ofSeconds(2));

        try (ResumeDocumentReader reader = reader(uploadLimited)) {
            assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                    "resume.pdf", PDF_MIME, pdf("too large"))))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("大小");
        }
        try (ResumeDocumentReader reader = reader(textLimited)) {
            assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                    "resume.pdf", PDF_MIME,
                    pdf("more than ten characters"))))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("文本长度");
        }
        assertTempDirectoryEmpty();
    }

    @Test
    void deletesRequestFilesAfterExtractorFailure() throws Exception {
        ResumeTextExtractor failing = extractor(DocumentType.PDF, content -> {
            throw new IllegalStateException("forced failure");
        });
        try (ResumeDocumentReader reader = new ResumeDocumentReader(
                properties(), List.of(failing, new PoiDocxResumeTextExtractor()))) {
            assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                    "resume.pdf", PDF_MIME, pdf("failure"))))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("解析失败");
        }
        assertTempDirectoryEmpty();
    }

    @Test
    void deletesRequestFilesAfterTimeoutAndInterruptsExtractor() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        ResumeTextExtractor blocking = extractor(DocumentType.PDF, content -> {
            try {
                while (true) {
                    Thread.sleep(1_000);
                }
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
        });
        ResumeParsingProperties shortTimeout = new ResumeParsingProperties(
                tempDirectory, DataSize.ofMegabytes(1), 100,
                DataSize.ofMegabytes(2), 10_000, Duration.ofMillis(30));

        try (ResumeDocumentReader reader = new ResumeDocumentReader(
                shortTimeout,
                List.of(blocking, new PoiDocxResumeTextExtractor()))) {
            assertThatThrownBy(() -> reader.extract(new ResumeUpload(
                    "resume.pdf", PDF_MIME, pdf("timeout"))))
                    .isInstanceOf(ResumeDocumentReader.ResumeParsingException.class)
                    .hasMessageContaining("超时");
        }
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertTempDirectoryEmpty();
    }

    private ResumeDocumentReader reader(ResumeParsingProperties properties) {
        return new ResumeDocumentReader(properties, List.of(
                new PdfBoxResumeTextExtractor(),
                new PoiDocxResumeTextExtractor()
        ));
    }

    private ResumeParsingProperties properties() {
        return new ResumeParsingProperties(
                tempDirectory,
                DataSize.ofMegabytes(2),
                100,
                DataSize.ofMegabytes(4),
                10_000,
                Duration.ofSeconds(2)
        );
    }

    private static ResumeTextExtractor extractor(
            DocumentType documentType,
            ThrowingExtraction extraction
    ) {
        return new ResumeTextExtractor() {
            @Override
            public DocumentType documentType() {
                return documentType;
            }

            @Override
            public Extraction extract(byte[] content) throws Exception {
                return extraction.extract(content);
            }
        };
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content =
                         new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(
                        Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] encryptedPdf() throws Exception {
        byte[] plain = pdf("secret resume");
        try (PDDocument document = Loader.loadPDF(plain);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            StandardProtectionPolicy protection = new StandardProtectionPolicy(
                    "owner-password", "user-password", new AccessPermission());
            protection.setEncryptionKeyLength(128);
            document.protect(protection);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] rawDocx(List<NamedContent> entries)
            throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (NamedContent entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private void assertTempDirectoryEmpty() throws Exception {
        try (var entries = Files.list(tempDirectory)) {
            assertThat(entries).isEmpty();
        }
    }

    private static NamedContent entry(String name, String content) {
        return new NamedContent(name, content);
    }

    private record NamedContent(String name, String content) {
    }

    @FunctionalInterface
    private interface ThrowingExtraction {
        ResumeTextExtractor.Extraction extract(byte[] content)
                throws Exception;
    }
}
