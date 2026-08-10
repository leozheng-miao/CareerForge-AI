package com.leo.careerforgeai.agent.application.tool.career;

import com.leo.careerforgeai.agent.application.tool.ToolExecutionException;
import com.leo.careerforgeai.agent.application.tool.career.search.CareerMaterialScopePolicy;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证模型文档类型过滤只能缩小服务端检索权限。
 * @author: Miao Zheng
 * @date: 2026-08-06 20:40
 **/
class CareerMaterialScopePolicyTest {

    private final CareerMaterialScopePolicy policy = new CareerMaterialScopePolicy();

    @Test
    @DisplayName("模型未指定文档类型时保持服务端Scope")
    void shouldKeepServerScopeWhenModelDoesNotRequestTypes() {
        RetrievalScope serverScope = new RetrievalScope(
                "careerforge",
                Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE),
                Set.of("document-1")
        );

        RetrievalScope result = policy.narrow(serverScope, Set.of());

        assertThat(result).isSameAs(serverScope);
    }

    @Test
    @DisplayName("服务端允许全部类型时使用模型指定类型缩小范围")
    void shouldNarrowUnrestrictedServerTypesWithRequestedTypes() {
        RetrievalScope serverScope = new RetrievalScope(
                "careerforge",
                Set.of(),
                Set.of("document-1")
        );

        RetrievalScope result = policy.narrow(
                serverScope,
                Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE)
        );

        assertThat(result.knowledgeBaseId()).isEqualTo("careerforge");
        assertThat(result.documentTypes())
                .containsExactly(KnowledgeDocumentType.INTERVIEW_EXPERIENCE);
        assertThat(result.documentIds()).containsExactly("document-1");
    }

    @Test
    @DisplayName("模型类型与服务端类型取交集并保留文档ID限制")
    void shouldIntersectRequestedTypesWithServerTypes() {
        RetrievalScope serverScope = new RetrievalScope(
                "careerforge",
                Set.of(
                        KnowledgeDocumentType.JOB_DESCRIPTION,
                        KnowledgeDocumentType.INTERVIEW_EXPERIENCE
                ),
                Set.of("document-1", "document-2")
        );

        RetrievalScope result = policy.narrow(
                serverScope,
                Set.of(KnowledgeDocumentType.JOB_DESCRIPTION)
        );

        assertThat(result.documentTypes())
                .containsExactly(KnowledgeDocumentType.JOB_DESCRIPTION);
        assertThat(result.documentIds())
                .containsExactlyInAnyOrder("document-1", "document-2");
    }

    @Test
    @DisplayName("模型请求完全越权的文档类型时拒绝执行")
    void shouldRejectRequestedTypesOutsideServerScope() {
        RetrievalScope serverScope = new RetrievalScope(
                "careerforge",
                Set.of(KnowledgeDocumentType.JOB_DESCRIPTION),
                Set.of()
        );

        assertThatThrownBy(() -> policy.narrow(
                serverScope,
                Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE)
        ))
                .isInstanceOfSatisfying(ToolExecutionException.class, exception ->
                        assertThat(exception.getErrorType())
                                .isEqualTo(ToolExecutionErrorType.SCOPE_VIOLATION))
                .hasMessage("请求文档类型超出服务端允许范围");
    }
}