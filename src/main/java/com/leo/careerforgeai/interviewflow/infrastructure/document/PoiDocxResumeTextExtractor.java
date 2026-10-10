package com.leo.careerforgeai.interviewflow.infrastructure.document;

import com.leo.careerforgeai.interviewflow.application.resume.ExtractedResumeText.DocumentType;
import com.leo.careerforgeai.interviewflow.application.resume.ResumeTextExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public final class PoiDocxResumeTextExtractor implements ResumeTextExtractor {
    @Override
    public DocumentType documentType() {
        return DocumentType.DOCX;
    }

    @Override
    public Extraction extract(byte[] content) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            int sourceUnits = Math.max(
                    1,
                    document.getParagraphs().size() + document.getTables().size()
            );
            return new Extraction(extractor.getText(), sourceUnits);
        }
    }
}
