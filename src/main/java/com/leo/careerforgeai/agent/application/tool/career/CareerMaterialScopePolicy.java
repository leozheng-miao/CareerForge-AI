package com.leo.careerforgeai.agent.application.tool.career;

import com.leo.careerforgeai.agent.application.tool.ToolExecutionException;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 将模型请求的文档类型限制合并进服务端RetrievalScope，并禁止扩大知识库权限。
 * @author: Miao Zheng
 * @date: 2026-08-06 20:40
 **/
public final class CareerMaterialScopePolicy {

    /** 使用模型请求的文档类型缩小服务端Scope，同时保留知识库和文档ID限制。 */
    public RetrievalScope narrow(
            RetrievalScope serverScope,
            Set<KnowledgeDocumentType> requestedDocumentTypes
    ) {
        Objects.requireNonNull(serverScope, "serverScope 不能为空");
        Objects.requireNonNull(requestedDocumentTypes, "requestedDocumentTypes 不能为空");

        if (requestedDocumentTypes.isEmpty()) return serverScope;

        Set<KnowledgeDocumentType> allowedDocumentTypes = serverScope.documentTypes();
        if (allowedDocumentTypes.isEmpty()) {
            return new RetrievalScope(
                    serverScope.knowledgeBaseId(),
                    requestedDocumentTypes,
                    serverScope.documentIds()
            );
        }

        EnumSet<KnowledgeDocumentType> narrowedTypes = EnumSet.copyOf(allowedDocumentTypes);
        narrowedTypes.retainAll(requestedDocumentTypes);

        if (narrowedTypes.isEmpty()) {
            throw new ToolExecutionException(
                    ToolExecutionErrorType.SCOPE_VIOLATION,
                    "请求文档类型超出服务端允许范围"
            );
        }

        return new RetrievalScope(
                serverScope.knowledgeBaseId(),
                narrowedTypes,
                serverScope.documentIds()
        );
    }
}