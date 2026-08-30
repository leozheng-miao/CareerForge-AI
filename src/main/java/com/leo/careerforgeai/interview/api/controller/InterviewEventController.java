package com.leo.careerforgeai.interview.api.controller;

import com.leo.careerforgeai.interview.api.sse.InterviewSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户模拟面试安全SSE事件和Last-Event-ID续读API
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@RestController
@RequestMapping("/api/mock-interviews")
@Tag(name = "Mock Interview Event")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewEventController {

    private final InterviewSseService sseService;

    public InterviewEventController(InterviewSseService sseService) {
        this.sseService = Objects.requireNonNull(sseService, "sseService不能为空");
    }

    @GetMapping(value = "/{interviewId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "观察模拟面试安全事件并支持Last-Event-ID断线续读")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "建立SSE事件流"),
            @ApiResponse(responseCode = "400", description = "interviewId或Last-Event-ID格式不合法"),
            @ApiResponse(responseCode = "404", description = "模拟面试不存在或不属于当前用户")
    })
    public SseEmitter events(
            @PathVariable UUID interviewId,
            @Parameter(description = "最后成功接收的Redis Stream事件ID，例如1725000000000-0")
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        return sseService.open(interviewId, lastEventId);
    }
}