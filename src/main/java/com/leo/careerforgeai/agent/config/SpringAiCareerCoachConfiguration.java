package com.leo.careerforgeai.agent.config;

import com.leo.careerforgeai.agent.application.coach.CareerCoachDefinition;
import com.leo.careerforgeai.agent.application.coach.CareerCoachFinalAnswerValidator;
import com.leo.careerforgeai.agent.application.coach.CareerCoachScopeProvider;
import com.leo.careerforgeai.agent.application.loop.ToolCallFingerprintService;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.infrastructure.springai.advisor.SpringAiBoundedToolCallingAdvisor;
import com.leo.careerforgeai.agent.infrastructure.springai.coach.SpringAiCareerCoachService;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.SpringAiBoundedToolCallingManager;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.SpringAiToolCallbackCatalog;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * @program: CareerForge-AI
 * @description: 按开关装配Spring AI Career Coach对照实现，不影响原生Agent链路。
 * @author: Miao Zheng
 * @date: 2026-08-10 02:20
 **/
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "careerforge.agent.spring-ai", name = "enabled", havingValue = "true")
public class SpringAiCareerCoachConfiguration {

    @Bean
    public SpringAiCareerCoachService springAiCareerCoachService(
            ChatModel chatModel,
            ObjectProvider<ObservationRegistry> observationRegistryProvider,
            CareerCoachFinalAnswerValidator finalAnswerValidator,
            CareerCoachScopeProvider scopeProvider,
            SpringAiToolCallbackCatalog callbackCatalog,
            AgentLoopPolicy policy,
            ToolCallFingerprintService fingerprintService,
            Clock agentClock
    ) {
        ObservationRegistry observationRegistry =
                observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP);
        ToolCallingManager defaultManager = ToolCallingManager.builder()
                .observationRegistry(observationRegistry)
                .build();
        ToolCallingManager boundedManager = new SpringAiBoundedToolCallingManager(
                defaultManager,
                policy,
                fingerprintService,
                CareerCoachDefinition.CONTEXT_VERSION
        );

        ChatClient chatClient = ChatClient.builder(
                chatModel,
                observationRegistry,
                null,
                null,
                SpringAiBoundedToolCallingAdvisor.builder(policy, agentClock)
                        .toolCallingManager(boundedManager)
        ).build();

        return new SpringAiCareerCoachService(
                chatClient,
                finalAnswerValidator,
                scopeProvider,
                callbackCatalog.callbacks(),
                policy,
                agentClock
        );
    }
}