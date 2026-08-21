package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.run.submission.CoachingRunRequestFingerprintService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证相同规范化请求生成相同指纹且不同业务输入不能被错误重放
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class CoachingRunRequestFingerprintServiceTest {

    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final CoachingRunRequestFingerprintService service =
            new CoachingRunRequestFingerprintService();

    @Test
    void shouldReuseFingerprintForSameNormalizedRequest() {
        String first = service.fingerprint(
                SESSION_ID,
                4,
                "  请解释Java并发  "
        );
        String second = service.fingerprint(
                SESSION_ID,
                4,
                "请解释Java并发"
        );

        assertThat(first)
                .isEqualTo(second)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void shouldSeparateDifferentRequestInputs() {
        String baseline = service.fingerprint(
                SESSION_ID,
                4,
                "请解释Java并发"
        );

        assertThat(service.fingerprint(
                SESSION_ID,
                5,
                "请解释Java并发"
        )).isNotEqualTo(baseline);

        assertThat(service.fingerprint(
                UUID.fromString(
                        "10000000-0000-0000-0000-000000000002"
                ),
                4,
                "请解释Java并发"
        )).isNotEqualTo(baseline);

        assertThat(service.fingerprint(
                SESSION_ID,
                4,
                "请解释Java并发与事务"
        )).isNotEqualTo(baseline);
    }

    @Test
    void shouldRejectInvalidInput() {
        assertThatThrownBy(
                () -> service.fingerprint(
                        SESSION_ID,
                        -1,
                        "message"
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> service.fingerprint(
                        SESSION_ID,
                        0,
                        " "
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}