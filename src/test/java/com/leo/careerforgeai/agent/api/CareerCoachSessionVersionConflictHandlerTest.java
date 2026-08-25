package com.leo.careerforgeai.agent.api;

import com.leo.careerforgeai.agent.api.advice.CareerCoachApiExceptionHandler;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionVersionConflictException;
import com.leo.careerforgeai.shared.web.BaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证Session乐观锁冲突被稳定映射为HTTP 409
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class CareerCoachSessionVersionConflictHandlerTest {

    @Test
    void shouldMapSessionVersionConflictToHttp409() {
        CareerCoachApiExceptionHandler handler =
                new CareerCoachApiExceptionHandler();

        ResponseEntity<BaseResponse<?>> response =
                handler.handleSessionVersionConflict(
                        new CoachingSessionVersionConflictException(
                                "Session并发更新冲突"
                        )
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(40900);
        assertThat(response.getBody().getMessage())
                .isEqualTo("Session状态已经变化，请刷新后重新提交");
    }
}