package com.leo.careerforgeai.interviewflow.application.resume;

import java.util.Objects;

/** 不包含临时路径或原始文件标识的简历纯文本。 */
public record ExtractedResumeText(
        String text,
        DocumentType documentType,
        ExtractionMetadata metadata
) {
    public ExtractedResumeText {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text不能为空");
        }
        Objects.requireNonNull(documentType, "documentType不能为null");
        Objects.requireNonNull(metadata, "metadata不能为null");
    }

    public enum DocumentType {
        PDF,
        DOCX
    }

    public record ExtractionMetadata(
            long uploadBytes,
            int sourceUnits,
            int extractedCharacters
    ) {
        public ExtractionMetadata {
            if (uploadBytes < 1 || sourceUnits < 1 || extractedCharacters < 1) {
                throw new IllegalArgumentException("简历解析元数据不合法");
            }
        }
    }
}
