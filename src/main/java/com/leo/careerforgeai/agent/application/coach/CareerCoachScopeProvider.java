package com.leo.careerforgeai.agent.application.coach;

import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @program: CareerForge-AI
 * @description: 为原生与Spring AI Career Coach提供同一份服务端检索权限范围。
 * @author: Miao Zheng
 * @date: 2026-08-10 01:45
 **/
@Component
public final class CareerCoachScopeProvider {

    private final RetrievalScope scope;

    public CareerCoachScopeProvider(KnowledgeSourceProperties sourceProperties) {
        Objects.requireNonNull(sourceProperties, "sourceProperties不能为空");

        Set<KnowledgeDocumentType> allowedTypes = sourceProperties.getDocuments().stream()
                .map(KnowledgeSourceProperties.DocumentDefinition::documentType)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> allowedDocumentIds = sourceProperties.getDocuments().stream()
                .map(KnowledgeSourceProperties.DocumentDefinition::documentId)
                .collect(Collectors.toUnmodifiableSet());

        this.scope = new RetrievalScope(
                sourceProperties.getKnowledgeBaseId(),
                allowedTypes,
                allowedDocumentIds
        );
    }

    public RetrievalScope scope() {
        return scope;
    }
}