package com.leo.careerforgeai.agent.infrastructure.redis.health;

import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 使用PING验证Redis可用性并转换为稳定基础设施错误
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
@Component
public class RedisAvailabilityProbe {

    private static final String EXPECTED_RESPONSE = "PONG";

    private final RedisConnectionFactory connectionFactory;

    public RedisAvailabilityProbe(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory不能为空");
    }

    public void verifyAvailable() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            if (!EXPECTED_RESPONSE.equalsIgnoreCase(response)) {
                throw new RedisInfrastructureException(
                        RedisInfrastructureErrorType.UNEXPECTED_RESPONSE,
                        "Redis PING返回了非预期响应"
                );
            }
        } catch (QueryTimeoutException exception) {
            throw new RedisInfrastructureException(
                    RedisInfrastructureErrorType.TIMED_OUT,
                    "Redis PING超时",
                    exception
            );
        } catch (RedisConnectionFailureException exception) {
            throw new RedisInfrastructureException(
                    RedisInfrastructureErrorType.UNAVAILABLE,
                    "Redis当前不可用",
                    exception
            );
        } catch (DataAccessException exception) {
            throw new RedisInfrastructureException(
                    RedisInfrastructureErrorType.COMMAND_FAILED,
                    "Redis PING执行失败",
                    exception
            );
        }
    }
}