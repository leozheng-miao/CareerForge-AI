package com.leo.careerforgeai.knowledge.config;

import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 一句话作用：绑定并校验知识库根目录、知识库标识和允许导入的文档白名单
 * @author: Miao Zheng
 * @date: 2026-07-31 15:13
 **/
@ConfigurationProperties(prefix = "careerforge.knowledge.source", ignoreUnknownFields = false)
@Validated
@Getter
public final class KnowledgeSourceProperties {

    @NotBlank
    private final String knowledgeBaseId;

    @NotNull
    private final Path rootDirectory;

    @Valid
    @NotEmpty
    private final List<DocumentDefinition> documents;

    public KnowledgeSourceProperties(
            String knowledgeBaseId,
            Path rootDirectory,
            List<DocumentDefinition> documents
    ) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.rootDirectory = rootDirectory;
        this.documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public record DocumentDefinition(
            @NotBlank String documentId,
            @NotBlank String documentName,
            @NotNull KnowledgeDocumentType documentType,
            @NotBlank String relativePath
    ) {
    }


}