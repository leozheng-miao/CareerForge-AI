package com.leo.careerforgeai.knowledge.api;

import com.leo.careerforgeai.knowledge.api.dto.RagQueryRequest;
import com.leo.careerforgeai.knowledge.api.dto.RagQueryResponse;
import com.leo.careerforgeai.knowledge.application.query.RagQueryService;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: CareerForge-AI
 * @description: 提供基于固定知识库范围的带引用 RAG 问答接口
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
@RestController
@RequestMapping("/api/knowledge/rag")
@RequiredArgsConstructor
public class KnowledgeRagController {

    private final RagQueryService ragQueryService;
    private final KnowledgeSourceProperties sourceProperties;

    @PostMapping("/query")
    public BaseResponse<RagQueryResponse> query(@Valid @RequestBody RagQueryRequest request) {
        RetrievalScope scope = new RetrievalScope(
                sourceProperties.getKnowledgeBaseId(),
                request.documentTypes(),
                request.documentIds()
        );
        return ResultUtils.success(RagQueryResponse.from(ragQueryService.query(request.query(), scope)));
    }

}