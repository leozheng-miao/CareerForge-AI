package com.leo.careerforgeai.agent.config;

import com.leo.careerforgeai.CareerForgeAiApplication;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.infrastructure.springai.coach.SpringAiCareerCoachService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证Spring AI对照服务的条件装配以及与原生Career Coach链路并存。
 * @author: Miao Zheng
 * @date: 2026-08-10 03:20
 **/
@SpringBootTest(
        classes = CareerForgeAiApplication.class,
        properties = {
                "spring.ai.model.chat=none",
                "careerforge.agent.spring-ai.enabled=true",
                "careerforge.model.base-url=http://localhost",
                "careerforge.model.api-key=test-placeholder",
                "careerforge.model.name=test-model"
        }
)
@Import(SpringAiCareerCoachConfigurationTest.FakeChatClientConfiguration.class)
class SpringAiCareerCoachConfigurationTest {

    @Test
    @DisplayName("启用对照开关时同时保留原生和Spring AI服务")
    void shouldCreateBothImplementationsWhenSpringAiComparisonIsEnabled(
            @Autowired CareerCoachService nativeService,
            @Autowired SpringAiCareerCoachService springAiService
    ) {
        assertThat(nativeService).isNotNull();
        assertThat(springAiService).isNotNull();

        var result = springAiService.coach("请给我一般职业建议。");

        assertThat(result.answer().status())
                .isEqualTo(CareerCoachAnswerStatus.ANSWERED);
        assertThat(result.answer().answer()).isEqualTo("测试回答");
    }

    /**
     * @program: CareerForge-AI
     * @description: 为条件装配测试提供不访问真实模型的ChatModel。
     * @author: Miao Zheng
     * @date: 2026-08-10 03:20
     **/
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeChatClientConfiguration {

        @Bean
        ChatModel testChatModel() {
            return prompt -> new ChatResponse(List.of(
                    new Generation(new AssistantMessage("""
                    {"status":"ANSWERED","answer":"测试回答","citedChunkIds":[]}
                    """))
            ));
        }
    }
}