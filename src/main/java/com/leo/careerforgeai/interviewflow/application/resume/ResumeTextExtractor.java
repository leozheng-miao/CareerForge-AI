package com.leo.careerforgeai.interviewflow.application.resume;

import com.leo.careerforgeai.interviewflow.application.resume.ExtractedResumeText.DocumentType;

/** 只对已经过签名和资源限制检查的文档内容做文本提取。 */
public interface ResumeTextExtractor {
    DocumentType documentType();

    Extraction extract(byte[] content) throws Exception;

    record Extraction(String text, int sourceUnits) {
        public Extraction {
            if (text == null || sourceUnits < 1) {
                throw new IllegalArgumentException("文本提取结果不合法");
            }
        }
    }
}
