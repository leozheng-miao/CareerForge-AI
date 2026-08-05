package com.leo.careerforgeai.knowledge.infrastructure.document.chunking;

import com.leo.careerforgeai.knowledge.domain.document.CleanedDocument;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 根据知识库、原始文档版本、清洗版本、Chunking 策略和位置生成确定性的 SHA-256 Chunk ID
 * @author: Miao Zheng
 * @date: 2026-07-31 16:57
 **/
@Component
public class StableChunkIdGenerator {
    public String generate(CleanedDocument document, String chunkerVersion, int chunkIndex) {
        if (document == null) throw new IllegalArgumentException("document 不能为空");
        if (chunkerVersion == null || chunkerVersion.isBlank()) throw new IllegalArgumentException("chunkerVersion 不能为空");
        if (chunkIndex < 0) throw new IllegalArgumentException("chunkIndex 不能小于 0");

        SourceDocument source = document.sourceDocument();
        String canonicalValue = canonicalize(List.of(
                source.knowledgeBaseId(),
                source.documentId(),
                source.sourceHash(),
                document.cleaningVersion(),
                chunkerVersion,
                Integer.toString(chunkIndex)
        ));

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private String canonicalize(List<String> values) {
        StringBuilder result = new StringBuilder();
        values.forEach(value -> result.append(value.length()).append(':').append(value));
        return result.toString();
    }
}