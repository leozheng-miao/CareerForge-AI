package com.leo.careerforgeai.agent.infrastructure.redis.health;

import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Redis可用性探测、Fake端口和稳定错误分类
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
@ExtendWith(MockitoExtension.class)
class RedisAvailabilityProbeTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    @Test
    void shouldAcceptPongAndCloseConnection() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        RedisAvailabilityProbe probe = new RedisAvailabilityProbe(connectionFactory);

        assertThatCode(probe::verifyAvailable).doesNotThrowAnyException();
        verify(connection).close();
    }

    @Test
    void shouldClassifyConnectionFailureAsUnavailable() {
        when(connectionFactory.getConnection()).thenThrow(new RedisConnectionFailureException("connection refused"));

        RedisAvailabilityProbe probe = new RedisAvailabilityProbe(connectionFactory);

        assertThatThrownBy(probe::verifyAvailable)
                .isInstanceOfSatisfying(RedisInfrastructureException.class, exception -> {
                    assertThat(exception.errorType()).isEqualTo(RedisInfrastructureErrorType.UNAVAILABLE);
                    assertThat(exception.getCause()).isInstanceOf(RedisConnectionFailureException.class);
                });
    }

    @Test
    void shouldClassifyCommandTimeout() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenThrow(new QueryTimeoutException("timeout"));

        RedisAvailabilityProbe probe = new RedisAvailabilityProbe(connectionFactory);

        assertThatThrownBy(probe::verifyAvailable)
                .isInstanceOfSatisfying(RedisInfrastructureException.class, exception ->
                        assertThat(exception.errorType()).isEqualTo(RedisInfrastructureErrorType.TIMED_OUT));

        verify(connection).close();
    }

    @Test
    void shouldClassifyOtherDataAccessFailure() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenThrow(new DataAccessResourceFailureException("command failed"));

        RedisAvailabilityProbe probe = new RedisAvailabilityProbe(connectionFactory);

        assertThatThrownBy(probe::verifyAvailable)
                .isInstanceOfSatisfying(RedisInfrastructureException.class, exception ->
                        assertThat(exception.errorType()).isEqualTo(RedisInfrastructureErrorType.COMMAND_FAILED));

        verify(connection).close();
    }

    @Test
    void shouldRejectUnexpectedPingResponse() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn(null);

        RedisAvailabilityProbe probe = new RedisAvailabilityProbe(connectionFactory);

        assertThatThrownBy(probe::verifyAvailable)
                .isInstanceOfSatisfying(RedisInfrastructureException.class, exception ->
                        assertThat(exception.errorType()).isEqualTo(RedisInfrastructureErrorType.UNEXPECTED_RESPONSE));

        verify(connection).close();
    }

    @Test
    void shouldClassifyUnusedFakePortAsUnavailable() throws IOException {
        int fakePort = findUnusedLocalPort();
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration("127.0.0.1", fakePort);
        SocketOptions socketOptions = SocketOptions.builder().connectTimeout(Duration.ofMillis(200)).build();
        ClientOptions clientOptions = ClientOptions.builder().socketOptions(socketOptions).build();
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .shutdownTimeout(Duration.ZERO)
                .clientOptions(clientOptions)
                .build();
        LettuceConnectionFactory fakeConnectionFactory = new LettuceConnectionFactory(server, client);
        fakeConnectionFactory.afterPropertiesSet();
        fakeConnectionFactory.start();

        try {
            RedisAvailabilityProbe probe = new RedisAvailabilityProbe(fakeConnectionFactory);
            assertThatThrownBy(probe::verifyAvailable)
                    .isInstanceOfSatisfying(RedisInfrastructureException.class, exception ->
                            assertThat(exception.errorType()).isEqualTo(RedisInfrastructureErrorType.UNAVAILABLE));
        } finally {
            fakeConnectionFactory.destroy();
        }
    }

    private static int findUnusedLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}