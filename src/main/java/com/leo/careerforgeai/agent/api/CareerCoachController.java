package com.leo.careerforgeai.agent.api;

import com.leo.careerforgeai.agent.api.dto.CareerCoachRequest;
import com.leo.careerforgeai.agent.api.dto.CareerCoachResponse;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
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
 * @description: 提供只接受用户消息且不允许客户端控制Prompt、工具和检索范围的Career Coach API。
 * @author: Miao Zheng
 * @date: 2026-08-07 06:20
 **/
@RestController
@RequestMapping("/api/agent/career-coach")
@RequiredArgsConstructor
public class CareerCoachController {

    private final CareerCoachService careerCoachService;

    /** 执行一次非流式Career Coach请求并返回可信回答和脱敏执行摘要。 */
    @PostMapping("/query")
    public BaseResponse<CareerCoachResponse> query(@Valid @RequestBody CareerCoachRequest request) {
        return ResultUtils.success(
                CareerCoachResponse.from(careerCoachService.coach(request.message()))
        );
    }
}