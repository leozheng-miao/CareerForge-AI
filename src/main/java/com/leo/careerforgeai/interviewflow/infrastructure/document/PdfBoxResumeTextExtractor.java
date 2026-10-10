package com.leo.careerforgeai.interviewflow.infrastructure.document;

import com.leo.careerforgeai.interviewflow.application.resume.ExtractedResumeText.DocumentType;
import com.leo.careerforgeai.interviewflow.application.resume.ResumeTextExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public final class PdfBoxResumeTextExtractor implements ResumeTextExtractor {
    @Override
    public DocumentType documentType() {
        return DocumentType.PDF;
    }

    @Override
    public Extraction extract(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new Extraction(
                    new PDFTextStripper().getText(document),
                    document.getNumberOfPages()
            );
        } catch (InvalidPasswordException exception) {
            throw new ResumeDocumentReader.ResumeParsingException(
                    ResumeDocumentReader.FailureReason.ENCRYPTED_PDF,
                    "不支持加密PDF简历",
                    exception
            );
        }
    }
}
