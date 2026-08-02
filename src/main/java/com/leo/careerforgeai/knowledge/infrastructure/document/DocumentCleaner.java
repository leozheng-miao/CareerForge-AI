package com.leo.careerforgeai.knowledge.infrastructure.document;

import com.leo.careerforgeai.knowledge.domain.CleanedDocument;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import org.springframework.stereotype.Component;

/**
 * @program: CareerForge-AI
 * @description: 把原始 Markdown 转换为换行和空白规则稳定、但语义结构不变的 CleanedDocument
 * @author: Miao Zheng
 * @date: 2026-07-31 16:25
 **/
@Component
public class DocumentCleaner {

    public static final String CLEANING_VERSION = "markdown-cleaner-v2";

    public CleanedDocument clean(SourceDocument sourceDocument) {
        if (sourceDocument == null) throw new IllegalArgumentException("sourceDocument 不能为空");

        String cleanedContent = normalize(sourceDocument.rawContent());
        if (cleanedContent.isBlank()) throw new DocumentCleanException(DocumentCleanErrorType.EMPTY_AFTER_CLEANING, "知识文档清洗后内容为空：" + sourceDocument.sourcePath());

        return new CleanedDocument(sourceDocument, CLEANING_VERSION, cleanedContent);
    }

    private String normalize(String rawContent) {
        String content = removeBom(rawContent).replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = content.split("\n", -1);
        StringBuilder result = new StringBuilder();
        String fenceMarker = null;
        boolean hasContent = false;
        boolean previousBlank = false;

        for (String line : lines) {
            if (fenceMarker != null) {
                appendLine(result, line);
                if (isClosingFence(line, fenceMarker)) fenceMarker = null;
                hasContent = true;
                previousBlank = false;
                continue;
            }

            if (isSourceMetadataLine(line)) continue;

            if (line.isBlank()) {
                if (hasContent && !previousBlank) appendLine(result, "");
                previousBlank = true;
                continue;
            }

            String openingFence = detectFenceMarker(line);
            appendLine(result, line);
            if (openingFence != null) fenceMarker = openingFence;
            hasContent = true;
            previousBlank = false;
        }

        if (fenceMarker == null) {
            while (!result.isEmpty() && result.charAt(result.length() - 1) == '\n') result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    private String removeBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }

    private String detectFenceMarker(String line) {
        String candidate = line.stripLeading();
        if (candidate.isEmpty()) return null;

        char marker = candidate.charAt(0);
        if (marker != '`' && marker != '~') return null;

        int length = 0;
        while (length < candidate.length() && candidate.charAt(length) == marker) length++;
        return length >= 3 ? candidate.substring(0, length) : null;
    }

    private boolean isClosingFence(String line, String openingFence) {
        String candidate = line.stripLeading();
        char marker = openingFence.charAt(0);
        int length = 0;

        while (length < candidate.length() && candidate.charAt(length) == marker) length++;
        return length >= openingFence.length() && candidate.substring(length).isBlank();
    }

    private void appendLine(StringBuilder result, String line) {
        if (!result.isEmpty()) result.append('\n');
        result.append(line);
    }

    private boolean isSourceMetadataLine(String line) {
        String content = line.strip();
        return content.startsWith("> 来源：") || content.startsWith("> 来源:");
    }
}