package com.leo.careerforgeai.memory.application.extraction;

import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionInputIdentity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Memory提取Fingerprint的顺序无关性、内容版本隔离和来源边界
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
class MemoryExtractionFingerprintGeneratorTest {

    private static final ActorId ACTOR_ID = new ActorId("actor-a");
    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID TURN_1 =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TURN_2 =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    private final MemoryExtractionFingerprintGenerator generator =
            new MemoryExtractionFingerprintGenerator();

    @Test
    void shouldGenerateSameIdentityForSameTurnsInDifferentRequestOrder() {
        ConversationTurn firstTurn = userTurn(
                TURN_1,
                SESSION_ID,
                1,
                "我每周可以学习10小时"
        );
        ConversationTurn secondTurn = assistantTurn(
                TURN_2,
                SESSION_ID,
                2,
                "后续计划将遵守每周10小时限制"
        );

        MemoryExtractionInputIdentity firstIdentity =
                generator.generate(List.of(firstTurn, secondTurn));
        MemoryExtractionInputIdentity secondIdentity =
                generator.generate(List.of(secondTurn, firstTurn));

        assertThat(firstIdentity).isEqualTo(secondIdentity);
        assertThat(firstIdentity.sources())
                .extracting(source -> source.turnId())
                .containsExactly(TURN_1, TURN_2);
    }

    @Test
    void shouldChangeFingerprintWhenContentOrExtractorVersionChanges() {
        ConversationTurn original = userTurn(
                TURN_1,
                SESSION_ID,
                1,
                "我每周可以学习10小时"
        );
        ConversationTurn changed = userTurn(
                TURN_1,
                SESSION_ID,
                1,
                "我每周只能学习8小时"
        );

        MemoryExtractionInputIdentity originalIdentity =
                generator.generate(List.of(original));
        MemoryExtractionInputIdentity changedContentIdentity =
                generator.generate(List.of(changed));
        MemoryExtractionInputIdentity changedVersionIdentity =
                generator.generate(
                        List.of(original),
                        "memory-candidate-extractor-v2"
                );

        assertThat(changedContentIdentity.inputFingerprint())
                .isNotEqualTo(originalIdentity.inputFingerprint());
        assertThat(changedVersionIdentity.inputFingerprint())
                .isNotEqualTo(originalIdentity.inputFingerprint());
    }

    @Test
    void shouldRejectTurnsFromDifferentSessions() {
        ConversationTurn firstTurn = userTurn(
                TURN_1,
                SESSION_ID,
                1,
                "我每周可以学习10小时"
        );
        ConversationTurn secondTurn = userTurn(
                TURN_2,
                OTHER_SESSION_ID,
                1,
                "我每周可以学习8小时"
        );

        assertThatThrownBy(() ->
                generator.generate(List.of(firstTurn, secondTurn)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("提取来源必须属于同一个Session");
    }

    @Test
    void shouldRejectFailedTurn() {
        ConversationTurn failedTurn = ConversationTurn.failedAssistant(
                TURN_1,
                SESSION_ID,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                ACTOR_ID,
                1,
                "agent-run-1",
                "MODEL_OUTPUT_INVALID",
                NOW
        );

        assertThatThrownBy(() -> generator.generate(List.of(failedTurn)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有COMPLETED Turn可以生成提取来源快照");
    }

    private ConversationTurn userTurn(
            UUID turnId,
            UUID sessionId,
            long sequence,
            String content
    ) {
        return ConversationTurn.completedUser(
                turnId,
                sessionId,
                UUID.randomUUID(),
                ACTOR_ID,
                sequence,
                content,
                NOW
        );
    }

    private ConversationTurn assistantTurn(
            UUID turnId,
            UUID sessionId,
            long sequence,
            String content
    ) {
        return ConversationTurn.completedAssistant(
                turnId,
                sessionId,
                UUID.randomUUID(),
                ACTOR_ID,
                sequence,
                content,
                "agent-run-1",
                NOW
        );
    }
}