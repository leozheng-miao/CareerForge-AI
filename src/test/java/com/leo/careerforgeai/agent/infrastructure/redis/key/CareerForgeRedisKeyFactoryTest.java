package com.leo.careerforgeai.agent.infrastructure.redis.key;

import com.leo.careerforgeai.agent.config.CareerForgeRedisProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Redis Key环境隔离、owner哈希和受控Key组件
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
class CareerForgeRedisKeyFactoryTest {

    private static final ActorId OWNER_A = new ActorId("actor-a");
    private static final ActorId OWNER_B = new ActorId("actor-b");
    private static final UUID RUN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String OWNER_A_HASH =
            "f8da8efdd407a3f5331042929b9cfc1dac65ec9cb3c7e9dff5b4d1cd2b101717";

    private final CareerForgeRedisKeyFactory keyFactory = new CareerForgeRedisKeyFactory(
            new CareerForgeRedisProperties(
                    "careerforge",
                    "test",
                    Duration.ofMinutes(30),
                    500
            )
    );

    @Test
    void shouldCreateDeterministicRunEventStreamKeyWithoutRawOwnerId() {
        String key = keyFactory.runEventStreamKey(OWNER_A, RUN_ID);

        assertThat(key).isEqualTo(
                "careerforge:test:{" + OWNER_A_HASH + "}:run:" + RUN_ID + ":events"
        );
        assertThat(key).doesNotContain(OWNER_A.value());
        assertThat(keyFactory.runEventStreamKey(OWNER_A, RUN_ID)).isEqualTo(key);
    }

    @Test
    void shouldIsolateDifferentOwners() {
        String ownerAKey = keyFactory.runEventStreamKey(OWNER_A, RUN_ID);
        String ownerBKey = keyFactory.runEventStreamKey(OWNER_B, RUN_ID);

        assertThat(ownerAKey).isNotEqualTo(ownerBKey);
        assertThat(ownerAKey).doesNotContain(OWNER_A.value());
        assertThat(ownerBKey).doesNotContain(OWNER_B.value());
    }

    @Test
    void shouldCreateOwnerAndGlobalRateLimitKeys() {
        assertThat(keyFactory.ownerRateLimitKey(OWNER_A, "coaching-run-submit"))
                .isEqualTo("careerforge:test:{" + OWNER_A_HASH + "}:rate:coaching-run-submit");

        assertThat(keyFactory.globalRateLimitKey("coaching-run-submit"))
                .isEqualTo("careerforge:test:{global}:rate:coaching-run-submit");
    }

    @Test
    void shouldRejectUnsafeOperationComponent() {
        assertThatThrownBy(() -> keyFactory.ownerRateLimitKey(
                OWNER_A,
                "submit:{other-owner}"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation");
    }
}