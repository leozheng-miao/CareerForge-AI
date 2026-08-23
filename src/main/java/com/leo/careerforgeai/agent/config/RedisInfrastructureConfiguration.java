package com.leo.careerforgeai.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 配置只允许字符串键值和哈希字段的RedisTemplate
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
@Configuration(proxyBeanMethods = false)
public class RedisInfrastructureConfiguration {

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate careerForgeStringRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        Objects.requireNonNull(connectionFactory, "connectionFactory不能为空");

        RedisSerializer<String> stringSerializer = RedisSerializer.string();
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setEnableDefaultSerializer(false);
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.setStringSerializer(stringSerializer);
        return template;
    }
}