package com.leo.careerforgeai.knowledge.api;

import com.leo.careerforgeai.knowledge.api.dto.RetrievalDebugRequest;
import com.leo.careerforgeai.knowledge.api.dto.RetrievalDebugResponse;
import com.leo.careerforgeai.knowledge.application.KnowledgeRetrievalDebugService;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.infrastructure.document.KnowledgeSourceProperties;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/retrieval")
@RequiredArgsConstructor
public class KnowledgeRetrievalDebugController {

    private final KnowledgeRetrievalDebugService debugService;
    private final KnowledgeSourceProperties sourceProperties;

    @PostMapping("/debug")
    public BaseResponse<RetrievalDebugResponse> debug(@Valid @RequestBody RetrievalDebugRequest request) {
        RetrievalScope scope = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), request.documentTypes(), request.documentIds());
        return ResultUtils.success(RetrievalDebugResponse.from(debugService.debug(request.query(), scope)));
    }
}