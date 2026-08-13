package com.leo.careerforgeai.agent.api;

import com.leo.careerforgeai.agent.api.dto.CoachingMessageResponse;
import com.leo.careerforgeai.agent.api.dto.CoachingSessionResponse;
import com.leo.careerforgeai.agent.api.dto.CreateCoachingSessionRequest;
import com.leo.careerforgeai.agent.api.dto.SendCoachingMessageRequest;
import com.leo.careerforgeai.agent.application.coach.ConversationalCareerCoachApplicationService;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}