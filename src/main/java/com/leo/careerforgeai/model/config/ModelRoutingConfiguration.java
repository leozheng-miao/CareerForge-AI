package com.leo.careerforgeai.model.config;

import com.leo.careerforgeai.model.application.routing.TaskAwareModelRouter;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * @program: CareerForge-AI
 * @description: 创建经过启动期联合校验的模型路由器和DeepSeek适配器参数
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@Configuration(proxyBeanMethods = false)
public class ModelRoutingConfiguration {
    private static final String DEEPSEEK_PROVIDER = "deepseek";
    private static final String DEEPSEEK_DEFAULT_PROFILE = "deepseek-standard";

    @Bean
    public TaskAwareModelRouter taskAwareModelRouter(
            ModelRoutingProperties routingProperties,
            ModelTaskPolicyProperties taskPolicies
    ) {
        Map<ModelTaskType, List<ModelExecutionProfile>> routes = routingProperties.executionRoutes();
        taskPolicies.validateAgainst(routes);
        return new TaskAwareModelRouter(routes, routingProperties.version());
    }

    @Bean
    public ModelProperties deepSeekModelProperties(ModelRoutingProperties properties) {
        ModelRoutingProperties.Provider provider = properties.providers().get(DEEPSEEK_PROVIDER);
        ModelRoutingProperties.Profile profile = properties.profiles().get(DEEPSEEK_DEFAULT_PROFILE);
        if (provider == null) throw new IllegalArgumentException("缺少DeepSeek供应商配置");
        if (profile == null || !DEEPSEEK_PROVIDER.equals(profile.provider())) {
            throw new IllegalArgumentException("缺少有效的DeepSeek默认Profile");
        }
        return new ModelProperties(provider.baseUrl(), provider.apiKey(), profile.model());
    }
}