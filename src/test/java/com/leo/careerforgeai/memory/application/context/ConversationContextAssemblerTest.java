package com.leo.careerforgeai.memory.application.context;


import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @program: CareerForge-AI
 * @description: 验证结构化会话Context的用户隔离、完整轮次裁剪和Memory状态边界
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class ConversationContextAssemblerTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final ActorId ACTOR_B = new ActorId("actor-b");
    private static final UUID SESSION_ID = id("session");
    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    @Test
    void shouldKeepOnlyCompleteRoundsAndConfirmedMemories() {
        ConversationContextAssembler assembler =
                new ConversationContextAssembler(ConversationContextPolicy.defaults());

        UUID completedExchangeId = id("completed-exchange");
        UUID failedExchangeId = id("failed-exchange");

        ConversationTurn completedUser = user(1, completedExchangeId, ACTOR_A, SESSION_ID, "什么是乐观锁？");
        ConversationTurn completedAssistant =
                assistant(2, completedExchangeId, ACTOR_A, SESSION_ID, "通过版本号检测并发更新。");

        ConversationTurn failedUser = user(3, failedExchangeId, ACTOR_A, SESSION_ID, "继续解释");
        ConversationTurn failedAssistant =
                failedAssistant(4, failedExchangeId, ACTOR_A, SESSION_ID, "MODEL_TIMEOUT");

        ConversationTurn currentUser =
                user(5, id("current-exchange"), ACTOR_A, SESSION_ID, "给我一个实际例子");

        MemoryItem confirmedMemory = confirmedMemory("用户熟悉Spring Boot");
        MemoryItem pendingMemory = pendingMemory("未经用户确认的信息");

        ConversationContext context = assembler.assemble(
                currentUser,
                List.of(completedUser, completedAssistant, failedUser, failedAssistant, currentUser),
                List.of(pendingMemory, confirmedMemory)
        );

        assertEquals(1, context.recentExchanges().size());
        assertEquals(completedExchangeId, context.recentExchanges().getFirst().exchangeId());
        assertEquals(1, context.confirmedMemories().size());
        assertEquals("用户熟悉Spring Boot", context.confirmedMemories().getFirst().content());
        assertEquals("给我一个实际例子", context.currentMessage());
        assertEquals(4, context.usage().messageCount());
    }

    @Test
    void shouldTrimOldestHistoryByCompleteExchange() {
        ConversationContextPolicy policy =
                new ConversationContextPolicy(2, 5, 0, 10_000, 10_000, 2);
        ConversationContextAssembler assembler = new ConversationContextAssembler(policy);

        UUID exchange1 = id("exchange-1");
        UUID exchange2 = id("exchange-2");
        UUID exchange3 = id("exchange-3");

        List<ConversationTurn> turns = List.of(
                user(1, exchange1, ACTOR_A, SESSION_ID, "问题1"),
                assistant(2, exchange1, ACTOR_A, SESSION_ID, "回答1"),
                user(3, exchange2, ACTOR_A, SESSION_ID, "问题2"),
                assistant(4, exchange2, ACTOR_A, SESSION_ID, "回答2"),
                user(5, exchange3, ACTOR_A, SESSION_ID, "问题3"),
                assistant(6, exchange3, ACTOR_A, SESSION_ID, "回答3")
        );

        ConversationTurn currentUser =
                user(7, id("current-exchange"), ACTOR_A, SESSION_ID, "当前问题");

        ConversationContext context = assembler.assemble(currentUser, turns, List.of());

        assertEquals(2, context.recentExchanges().size());
        assertEquals(exchange2, context.recentExchanges().get(0).exchangeId());
        assertEquals(exchange3, context.recentExchanges().get(1).exchangeId());
        assertEquals(5, context.usage().messageCount());
        assertTrue(context.usage().historyTrimmed());
    }

    @Test
    void shouldRejectHistoryOwnedByAnotherActor() {
        ConversationContextAssembler assembler =
                new ConversationContextAssembler(ConversationContextPolicy.defaults());

        ConversationTurn currentUser =
                user(2, id("current-exchange"), ACTOR_A, SESSION_ID, "当前问题");

        ConversationTurn anotherActorsTurn =
                user(1, id("foreign-exchange"), ACTOR_B, SESSION_ID, "其他用户的问题");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assembler.assemble(currentUser, List.of(anotherActorsTurn), List.of())
        );

        assertEquals("会话历史包含其他用户的数据", exception.getMessage());
    }

    @Test
    void shouldRejectCurrentMessageWhenItExceedsBudget() {
        ConversationContextPolicy policy =
                new ConversationContextPolicy(1, 3, 0, 5, 5, 1);
        ConversationContextAssembler assembler = new ConversationContextAssembler(policy);

        ConversationTurn currentUser =
                user(1, id("current-exchange"), ACTOR_A, SESSION_ID, "123456");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assembler.assemble(currentUser, List.of(), List.of())
        );

        assertEquals("当前消息超过Context预算", exception.getMessage());
    }

    private static ConversationTurn user(long sequence,
                                         UUID exchangeId,
                                         ActorId ownerId,
                                         UUID sessionId,
                                         String content) {
        return ConversationTurn.completedUser(
                id("user-" + sequence + "-" + ownerId.value()),
                sessionId,
                exchangeId,
                ownerId,
                sequence,
                content,
                NOW.plusSeconds(sequence)
        );
    }

    private static ConversationTurn assistant(long sequence,
                                              UUID exchangeId,
                                              ActorId ownerId,
                                              UUID sessionId,
                                              String content) {
        return ConversationTurn.completedAssistant(
                id("assistant-" + sequence + "-" + ownerId.value()),
                sessionId,
                exchangeId,
                ownerId,
                sequence,
                content,
                "agent-run-" + sequence,
                NOW.plusSeconds(sequence)
        );
    }

    private static ConversationTurn failedAssistant(long sequence,
                                                    UUID exchangeId,
                                                    ActorId ownerId,
                                                    UUID sessionId,
                                                    String failureCode) {
        return ConversationTurn.failedAssistant(
                id("failed-assistant-" + sequence),
                sessionId,
                exchangeId,
                ownerId,
                sequence,
                "failed-agent-run-" + sequence,
                failureCode,
                NOW.plusSeconds(sequence)
        );
    }

    private static MemoryItem confirmedMemory(String content) {
        MemoryItem pending = pendingMemory(content);
        MemoryDecision decision = MemoryDecision.create(
                id("decision-" + content),
                pending,
                ACTOR_A,
                MemoryDecisionType.CONFIRM,
                null,
                null,
                NOW.plusSeconds(1)
        );
        return pending.applyDecision(decision);
    }

    private static MemoryItem pendingMemory(String content) {
        UUID memoryId = id("memory-" + content);
        return MemoryItem.createPending(
                memoryId,
                ACTOR_A,
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence("SpringBoot"),
                content,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        "turn-" + memoryId,
                        "a".repeat(64)
                ),
                List.of("turn-" + memoryId),
                NOW
        );
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}