package com.leo.careerforgeai.memory.config;

import com.leo.careerforgeai.memory.application.context.ConversationContextAssembler;
import com.leo.careerforgeai.memory.application.context.ConversationContextPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: CareerForge-AI
 * @description: 装配会话Context预算策略和结构化Context组装器
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
@Configuration(proxyBeanMethods = false)
public class MemoryContextConfiguration {

    /** 创建首版服务端Context预算，客户端不能覆盖这些限制。 */
    @Bean
    public ConversationContextPolicy conversationContextPolicy() {
        return ConversationContextPolicy.defaults();
    }

    /** 创建负责用户隔离检查、完整轮次筛选和预算裁剪的Context组装器。 */
    @Bean
    public ConversationContextAssembler conversationContextAssembler(
            ConversationContextPolicy policy
    ) {
        return new ConversationContextAssembler(policy);
    }
}