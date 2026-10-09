package com.leo.careerforgeai.interviewflow.api.sse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.UUID;

@RestController
@RequestMapping("/api/interviews/{interviewId}")
@Tag(name = "Interview Flow Events")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewFlowEventController {
    private final InterviewFlowSseService service;
    public InterviewFlowEventController(InterviewFlowSseService service) { this.service = service; }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅仅包含ID、安全状态、版本和时间的面试进度")
    public SseEmitter events(@PathVariable UUID interviewId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return service.open(interviewId, lastEventId);
    }
}
