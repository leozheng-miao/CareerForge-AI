package com.leo.careerforgeai.agent.api;

import com.leo.careerforgeai.agent.api.dto.CoachingMessageResponse;
import com.leo.careerforgeai.agent.api.dto.CoachingSessionResponse;
import com.leo.careerforgeai.agent.api.dto.CoachingTurnResponse;
import com.leo.careerforgeai.agent.api.dto.CreateCoachingSessionRequest;
import com.leo.careerforgeai.agent.api.dto.SendCoachingMessageRequest;
import com.leo.careerforgeai.agent.application.coach.conversation.ConversationalCareerCoachApplicationService;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionQueryApplicationService;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSessionStatus;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供创建、查询和发送Career Coach会话消息的受控API
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
@RestController
@RequestMapping("/api/coaching-sessions")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingSessionController {

    private final CoachingSessionApplicationService sessionApplicationService;
    private final ConversationalCareerCoachApplicationService conversationalCareerCoachService;
    private final CoachingSessionQueryApplicationService sessionQueryApplicationService;

    /** 使用服务端当前Actor创建新会话。 */
    @PostMapping
    public BaseResponse<CoachingSessionResponse> create(
            @Valid @RequestBody CreateCoachingSessionRequest request
    ) {
        return ResultUtils.success(
                CoachingSessionResponse.from(
                        sessionApplicationService.createSession(request.title())
                )
        );
    }

    /** 查询当前Actor拥有的会话。 */
    @GetMapping("/{sessionId}")
    public BaseResponse<CoachingSessionResponse> get(
            @PathVariable UUID sessionId
    ) {
        return ResultUtils.success(
                CoachingSessionResponse.from(
                        sessionApplicationService.getSession(sessionId)
                )
        );
    }

    /** 分页查询当前用户最近的Turn，供会话展示和Memory来源选择。 */
    @GetMapping("/{sessionId}/turns")
    @Operation(summary = "分页查询当前用户会话的Turn历史")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "返回按turnSequence升序排列的当前页"),
            @ApiResponse(responseCode = "400", description = "limit或cursor不合法"),
            @ApiResponse(responseCode = "404", description = "Session不存在或不属于当前用户")
    })
    public BaseResponse<CoachingTurnPageResponse> getTurns(
            @PathVariable UUID sessionId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResultUtils.success(CoachingTurnPageResponse.from(
                sessionQueryApplicationService.listTurns(sessionId, cursor, limit)
        ));
    }

    /** 在当前Actor拥有的ACTIVE会话中发送一条消息。 */
    @PostMapping("/{sessionId}/messages")
    public BaseResponse<CoachingMessageResponse> sendMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendCoachingMessageRequest request
    ) {
        return ResultUtils.success(
                CoachingMessageResponse.from(
                        conversationalCareerCoachService.coach(
                                sessionId,
                                request.expectedSessionVersion(),
                                request.message()
                        )
                )
        );
    }

    @GetMapping
    @Operation(summary = "分页查询当前用户的Career Coach会话")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "返回稳定排序的会话分页"),
            @ApiResponse(responseCode = "400", description = "limit、status或cursor不合法")
    })
    public BaseResponse<CoachingSessionPageResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) CoachingSessionStatus status,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResultUtils.success(CoachingSessionPageResponse.from(
                sessionQueryApplicationService.list(cursor, status, limit)
        ));
    }

    /**
     * @program: CareerForge-AI
     * @description: Career Coach会话分页响应
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页会话
     * @param nextCursor 下一页Cursor
     * @param hasMore 是否存在下一页
     */
    public record CoachingSessionPageResponse(
            List<CoachingSessionResponse> items,
            String nextCursor,
            boolean hasMore
    ) {
        static CoachingSessionPageResponse from(
                CoachingSessionQueryApplicationService.SessionPage page
        ) {
            return new CoachingSessionPageResponse(
                    page.items().stream().map(CoachingSessionResponse::from).toList(),
                    page.nextCursor(),
                    page.hasMore()
            );
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: Career Coach Turn历史分页响应
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页Turn
     * @param nextCursor 更早一页Cursor
     * @param hasMore 是否还有更早的Turn
     */
    public record CoachingTurnPageResponse(
            List<CoachingTurnResponse> items,
            String nextCursor,
            boolean hasMore
    ) {
        static CoachingTurnPageResponse from(
                CoachingSessionQueryApplicationService.TurnPage page
        ) {
            return new CoachingTurnPageResponse(
                    page.items().stream().map(CoachingTurnResponse::from).toList(),
                    page.nextCursor(),
                    page.hasMore()
            );
        }
    }
}