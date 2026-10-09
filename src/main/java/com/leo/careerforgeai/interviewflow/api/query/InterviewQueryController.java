package com.leo.careerforgeai.interviewflow.api.query;

import com.leo.careerforgeai.interviewflow.application.query.InterviewQueryService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/interviews")
@Tag(name = "Interview Flow Query")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewQueryController {
    private final InterviewQueryService service;
    public InterviewQueryController(InterviewQueryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "稳定游标查询当前用户面试记录")
    public BaseResponse<InterviewHistoryPageResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        Cursor parsed = decode(cursor);
        var values = service.list(parsed == null ? null : parsed.createdAt(),
                parsed == null ? null : parsed.interviewId(), limit + 1);
        boolean hasMore = values.size() > limit;
        var page = values.stream().limit(limit).toList();
        String next = hasMore && !page.isEmpty() ? encode(page.getLast()) : null;
        return ResultUtils.success(new InterviewHistoryPageResponse(
                page.stream().map(InterviewHistoryPageResponse.Item::from).toList(), next));
    }

    @GetMapping("/{interviewId}/question")
    @Operation(summary = "通过REST读取当前完整题干和服务端草稿")
    public BaseResponse<CurrentQuestionResponse> question(@PathVariable UUID interviewId) {
        return ResultUtils.success(CurrentQuestionResponse.from(service.currentQuestion(interviewId)));
    }

    @GetMapping("/{interviewId}/history")
    @Operation(summary = "查询面试完整问答历史")
    public BaseResponse<List<InterviewQueryService.HistoryTurn>> history(
            @PathVariable UUID interviewId) {
        return ResultUtils.success(service.history(interviewId));
    }

    private static String encode(com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate item) {
        String raw = item.createdAt() + "|" + item.session().interviewId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
    private static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8).split("\\|", 2);
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("无效的历史游标", exception);
        }
    }
    private record Cursor(Instant createdAt, UUID interviewId) {}
}
