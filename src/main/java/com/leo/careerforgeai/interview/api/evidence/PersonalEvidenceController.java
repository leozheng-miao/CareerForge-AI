package com.leo.careerforgeai.interview.api.evidence;

import com.leo.careerforgeai.interview.api.dto.evidence.CreatePersonalEvidenceRequest;
import com.leo.careerforgeai.interview.api.dto.evidence.PersonalEvidenceResponse;
import com.leo.careerforgeai.interview.api.dto.evidence.PersonalEvidenceVersionRequest;
import com.leo.careerforgeai.interview.api.dto.evidence.UpdatePersonalEvidenceRequest;
import com.leo.careerforgeai.interview.application.evidence.PersonalEvidenceApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户个人证据创建、查询、版本更新和撤销API
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@RestController
@RequestMapping("/api/personal-evidence-artifacts")
@Tag(name = "Personal Evidence")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class PersonalEvidenceController {

    private final PersonalEvidenceApplicationService applicationService;

    public PersonalEvidenceController(PersonalEvidenceApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService不能为空");
    }

    @PostMapping
    @Operation(summary = "为当前用户创建文本或Markdown个人证据")
    public BaseResponse<PersonalEvidenceResponse> create(
            @Valid @RequestBody CreatePersonalEvidenceRequest request
    ) {
        return ResultUtils.success(PersonalEvidenceResponse.from(
                applicationService.create(request.type(), request.sourceName(), request.rawContent())
        ));
    }

    @GetMapping("/{artifactId}")
    @Operation(summary = "查询当前用户指定个人证据的ACTIVE版本")
    public BaseResponse<PersonalEvidenceResponse> get(@PathVariable UUID artifactId) {
        return ResultUtils.success(PersonalEvidenceResponse.from(applicationService.get(artifactId)));
    }

    @PostMapping("/{artifactId}/versions")
    @Operation(summary = "基于期望版本创建个人证据的新不可变版本")
    public BaseResponse<PersonalEvidenceResponse> update(
            @PathVariable UUID artifactId,
            @Valid @RequestBody UpdatePersonalEvidenceRequest request
    ) {
        return ResultUtils.success(PersonalEvidenceResponse.from(applicationService.update(
                artifactId,
                request.expectedVersion(),
                request.sourceName(),
                request.rawContent()
        )));
    }

    @PostMapping("/{artifactId}/revoke")
    @Operation(summary = "基于期望版本撤销个人证据的当前ACTIVE版本")
    public BaseResponse<PersonalEvidenceResponse> revoke(
            @PathVariable UUID artifactId,
            @Valid @RequestBody PersonalEvidenceVersionRequest request
    ) {
        return ResultUtils.success(PersonalEvidenceResponse.from(
                applicationService.revoke(artifactId, request.expectedVersion())
        ));
    }
}