package com.leo.careerforgeai.interviewflow.application.resume;

import java.util.Objects;

/** 仅在当前请求内存在的简历上传内容。 */
public record ResumeUpload(
        String originalFilename,
        String contentType,
        byte[] content
) {
    public ResumeUpload {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename不能为空");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType不能为空");
        }
        Objects.requireNonNull(content, "content不能为null");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
