package com.leo.careerforgeai.memory.application.context;


import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.profile.LearningPreferenceKey;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
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
        assertEquals(
                confirmedMemory.source().sourceType(),
                context.confirmedMemories().getFirst().sourceType()
        );
        assertEquals(
                confirmedMemory.source().sourceId(),
                context.confirmedMemories().getFirst().sourceId()
        );
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

    /**
     * @program: CareerForge-AI
     * @description: 验证CONFIRMED Memory按照相关性、类型和时间排序，并只消除完全重复记录
     * @author: Miao Zheng
     * @date: 2026-08-15
     **/
    @Test
    void shouldDeduplicateAndOrderConfirmedMemoriesDeterministically() {
        ConversationContextAssembler assembler =
                new ConversationContextAssembler(ConversationContextPolicy.defaults());

        MemoryItem springDuplicateOld = confirmedMemory(
                "spring-duplicate-old",
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence("Spring Boot"),
                "使用Spring Boot开发过REST接口",
                "source-spring-1",
                NOW.plusSeconds(1)
        );
        MemoryItem springDifferentSource = confirmedMemory(
                "spring-different-source",
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence("SpringBoot"),
                "使用Spring Boot开发过REST接口",
                "source-spring-2",
                NOW.plusSeconds(5)
        );
        MemoryItem springDuplicateNewest = confirmedMemory(
                "spring-duplicate-newest",
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence("Spring Boot"),
                "使用Spring Boot开发过REST接口",
                "source-spring-1",
                NOW.plusSeconds(10)
        );
        MemoryItem timeConstraint = confirmedMemory(
                "time-constraint",
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                "每周最多投入10小时",
                "source-time",
                NOW.plusSeconds(100)
        );
        MemoryItem careerGoal = confirmedMemory(
                "career-goal",
                MemoryType.CAREER_GOAL,
                MemoryNormalizedKey.careerGoal(),
                "目标是Java AI应用开发工程师",
                "source-goal",
                NOW.plusSeconds(200)
        );
        MemoryItem learningPreference = confirmedMemory(
                "learning-preference",
                MemoryType.LEARNING_PREFERENCE,
                MemoryNormalizedKey.learningPreference(
                        LearningPreferenceKey.CONTENT_FORMAT
                ),
                "偏好结合真实业务逐步学习",
                "source-preference",
                NOW.plusSeconds(300)
        );
        MemoryItem kafkaEvidence = confirmedMemory(
                "kafka-evidence",
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence("Kafka"),
                "理解Kafka消费者组",
                "source-kafka",
                NOW.plusSeconds(400)
        );

        ConversationTurn currentUser = user(
                1,
                id("current-selection-exchange"),
                ACTOR_A,
                SESSION_ID,
                "请重点分析我的Spring Boot能力"
        );

        ConversationContext context = assembler.assemble(
                currentUser,
                List.of(currentUser),
                List.of(
                        kafkaEvidence,
                        learningPreference,
                        springDuplicateOld,
                        careerGoal,
                        springDifferentSource,
                        timeConstraint,
                        springDuplicateNewest
                )
        );

        assertEquals(
                List.of(
                        springDuplicateNewest.memoryId(),
                        springDifferentSource.memoryId(),
                        timeConstraint.memoryId(),
                        careerGoal.memoryId(),
                        learningPreference.memoryId(),
                        kafkaEvidence.memoryId()
                ),
                context.confirmedMemories()
                        .stream()
                        .map(ConversationContext.ConfirmedMemoryFact::memoryId)
                        .toList()
        );
        assertEquals(6, context.usage().memoryCount());
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证Memory预算按最终JSON消息计算，并跳过过大Memory继续选择后续较小Memory
     * @author: Miao Zheng
     * @date: 2026-08-15
     **/
    @Test
    void shouldCountFormattedMemoryAndSkipOversizedMemoryWithoutBlockingSmallerOne() {
        MemoryItem oversizedMemory = confirmedMemory(
                "oversized-time-constraint",
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(
                        TimeConstraintKey.WEEKLY_HOURS
                ),
                "超大时间约束".repeat(120),
                "source-oversized",
                NOW.plusSeconds(20)
        );
        MemoryItem smallerMemory = confirmedMemory(
                "smaller-career-goal",
                MemoryType.CAREER_GOAL,
                MemoryNormalizedKey.careerGoal(),
                "目标是Java AI应用开发工程师",
                "source-smaller",
                NOW.plusSeconds(10)
        );

        ConversationTurn currentUser = user(
                1,
                id("budget-current-exchange"),
                ACTOR_A,
                SESSION_ID,
                "请给出下一步建议"
        );

        ConversationContext.ConfirmedMemoryFact smallerFact =
                toMemoryFact(smallerMemory);

        int expectedMemoryChars =
                ConfirmedMemoryContextFormatter
                        .format(List.of(smallerFact))
                        .length();

        int maxContentChars =
                currentUser.content().length() + expectedMemoryChars;

        ConversationContextPolicy policy =
                new ConversationContextPolicy(
                        0,
                        2,
                        10,
                        maxContentChars,
                        maxContentChars,
                        1
                );

        ConversationContextAssembler assembler =
                new ConversationContextAssembler(policy);

        ConversationContext context = assembler.assemble(
                currentUser,
                List.of(currentUser),
                List.of(smallerMemory, oversizedMemory)
        );

        assertEquals(
                List.of(smallerMemory.memoryId()),
                context.confirmedMemories()
                        .stream()
                        .map(ConversationContext.ConfirmedMemoryFact::memoryId)
                        .toList()
        );
        assertEquals(maxContentChars, context.usage().contentChars());
        assertEquals(maxContentChars, context.usage().estimatedTokens());
        assertEquals(1, context.usage().memoryCount());
        assertTrue(context.usage().memoriesTrimmed());
    }

    /**
     * @program: CareerForge-AI
     * @description: MC-CTX-001验证CONFIRMED时间约束相对无Memory基线提供的数据收益及预算成本
     * @author: Miao Zheng
     * @date: 2026-08-15
     **/
    @Test
    void shouldMeasureConfirmedMemoryAvailabilityAndBudgetCostAgainstNoMemoryBaseline() {
        ConversationContextPolicy policy = ConversationContextPolicy.defaults();
        ConversationContextAssembler assembler =
                new ConversationContextAssembler(policy);

        ConversationTurn currentUser = user(
                1,
                id("benefit-current-exchange"),
                ACTOR_A,
                SESSION_ID,
                "请根据我每周可以投入的时间安排下一步学习"
        );

        MemoryItem weeklyHours = confirmedMemory(
                "benefit-weekly-hours",
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(
                        TimeConstraintKey.WEEKLY_HOURS
                ),
                "每周最多投入六小时",
                "source-weekly-hours",
                NOW.plusSeconds(10)
        );

        ConversationContext withoutMemory = assembler.assemble(
                currentUser,
                List.of(currentUser),
                List.of()
        );

        ConversationContext withMemory = assembler.assemble(
                currentUser,
                List.of(currentUser),
                List.of(weeklyHours)
        );

        String formattedMemoryContext =
                ConfirmedMemoryContextFormatter.format(
                        withMemory.confirmedMemories()
                );

        assertEquals(0, withoutMemory.usage().memoryCount());
        assertEquals(1, withoutMemory.usage().messageCount());
        assertTrue(withoutMemory.confirmedMemories().isEmpty());

        assertEquals(1, withMemory.usage().memoryCount());
        assertEquals(2, withMemory.usage().messageCount());
        assertEquals(
                weeklyHours.memoryId(),
                withMemory.confirmedMemories().getFirst().memoryId()
        );
        assertEquals(
                "每周最多投入六小时",
                withMemory.confirmedMemories().getFirst().content()
        );
        assertEquals(
                MemorySourceType.CONVERSATION_TURN,
                withMemory.confirmedMemories().getFirst().sourceType()
        );
        assertEquals(
                "source-weekly-hours",
                withMemory.confirmedMemories().getFirst().sourceId()
        );

        assertEquals(
                formattedMemoryContext.length(),
                withMemory.usage().contentChars()
                        - withoutMemory.usage().contentChars()
        );
        assertEquals(
                policy.estimateTokens(withMemory.usage().contentChars()),
                withMemory.usage().estimatedTokens()
        );
        assertTrue(
                withMemory.usage().contentChars()
                        <= policy.maxContentChars()
        );
        assertTrue(
                withMemory.usage().estimatedTokens()
                        <= policy.maxEstimatedTokens()
        );
        assertEquals(
                withoutMemory.currentMessage(),
                withMemory.currentMessage()
        );
        assertTrue(withMemory.recentExchanges().isEmpty());
        assertTrue(!withMemory.usage().memoriesTrimmed());
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证PENDING、REJECTED、SUPERSEDED和REVOKED均不能进入Context
     * @author: Miao Zheng
     * @date: 2026-08-15
     **/
    @Test
    void shouldExcludeEveryInactiveMemoryStatusFromContext() {
        ConversationContextAssembler assembler =
                new ConversationContextAssembler(
                        ConversationContextPolicy.defaults()
                );

        MemoryItem pending = memoryWithStatus(
                "inactive-pending",
                ACTOR_A,
                MemoryStatus.PENDING,
                NOW.plusSeconds(10)
        );
        MemoryItem rejected = memoryWithStatus(
                "inactive-rejected",
                ACTOR_A,
                MemoryStatus.REJECTED,
                NOW.plusSeconds(20)
        );
        MemoryItem superseded = memoryWithStatus(
                "inactive-superseded",
                ACTOR_A,
                MemoryStatus.SUPERSEDED,
                NOW.plusSeconds(30)
        );
        MemoryItem revoked = memoryWithStatus(
                "inactive-revoked",
                ACTOR_A,
                MemoryStatus.REVOKED,
                NOW.plusSeconds(40)
        );
        MemoryItem confirmed = memoryWithStatus(
                "active-confirmed",
                ACTOR_A,
                MemoryStatus.CONFIRMED,
                NOW.plusSeconds(50)
        );

        ConversationTurn currentUser = user(
                1,
                id("inactive-status-exchange"),
                ACTOR_A,
                SESSION_ID,
                "请结合我的已确认能力给出建议"
        );

        ConversationContext context = assembler.assemble(
                currentUser,
                List.of(currentUser),
                List.of(
                        pending,
                        rejected,
                        superseded,
                        revoked,
                        confirmed
                )
        );

        assertEquals(
                List.of(confirmed.memoryId()),
                context.confirmedMemories()
                        .stream()
                        .map(ConversationContext.ConfirmedMemoryFact::memoryId)
                        .toList()
        );
        assertEquals(1, context.usage().memoryCount());
        assertTrue(!context.usage().memoriesTrimmed());
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证Repository异常返回其他owner的Memory时Assembler必须失败关闭
     * @author: Miao Zheng
     * @date: 2026-08-15
     **/
    @Test
    void shouldFailClosedWhenMemoryListContainsAnotherOwner() {
        ConversationContextAssembler assembler =
                new ConversationContextAssembler(
                        ConversationContextPolicy.defaults()
                );

        ConversationTurn currentUser = user(
                1,
                id("foreign-memory-exchange"),
                ACTOR_A,
                SESSION_ID,
                "请结合我的能力给出建议"
        );

        MemoryItem foreignMemory = memoryWithStatus(
                "foreign-confirmed",
                ACTOR_B,
                MemoryStatus.CONFIRMED,
                NOW.plusSeconds(10)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> assembler.assemble(
                        currentUser,
                        List.of(currentUser),
                        List.of(foreignMemory)
                )
        );

        assertEquals(
                "Memory列表包含其他用户的数据",
                exception.getMessage()
        );
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

    private static MemoryItem confirmedMemory(
            String idSeed,
            MemoryType type,
            MemoryNormalizedKey normalizedKey,
            String content,
            String sourceId,
            Instant confirmedAt
    ) {
        MemoryItem pending = MemoryItem.createPending(
                id("memory-" + idSeed),
                ACTOR_A,
                type,
                normalizedKey,
                content,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        sourceId,
                        "a".repeat(64)
                ),
                List.of(sourceId),
                confirmedAt.minusSeconds(1)
        );

        MemoryDecision decision = MemoryDecision.create(
                id("decision-" + idSeed),
                pending,
                ACTOR_A,
                MemoryDecisionType.CONFIRM,
                null,
                "固定Context选择Case",
                confirmedAt
        );

        return pending.applyDecision(decision);
    }

    private static ConversationContext.ConfirmedMemoryFact toMemoryFact(
            MemoryItem memory
    ) {
        return new ConversationContext.ConfirmedMemoryFact(
                memory.memoryId(),
                memory.type(),
                memory.normalizedKey(),
                memory.source().sourceType(),
                memory.source().sourceId(),
                memory.content()
        );
    }

    private static MemoryItem memoryWithStatus(
            String idSeed,
            ActorId ownerId,
            MemoryStatus targetStatus,
            Instant createdAt
    ) {
        MemoryItem pending = MemoryItem.createPending(
                id("status-memory-" + idSeed),
                ownerId,
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence(idSeed),
                "固定状态边界内容-" + idSeed,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        "status-source-" + idSeed,
                        "a".repeat(64)
                ),
                List.of("status-source-" + idSeed),
                createdAt
        );

        if (targetStatus == MemoryStatus.PENDING) {
            return pending;
        }
        if (targetStatus == MemoryStatus.REJECTED) {
            return applyDecision(
                    pending,
                    MemoryDecisionType.REJECT,
                    null,
                    createdAt.plusSeconds(1)
            );
        }

        MemoryItem confirmed = applyDecision(
                pending,
                MemoryDecisionType.CONFIRM,
                null,
                createdAt.plusSeconds(1)
        );

        return switch (targetStatus) {
            case CONFIRMED -> confirmed;
            case SUPERSEDED -> applyDecision(
                    confirmed,
                    MemoryDecisionType.SUPERSEDE,
                    id("replacement-" + idSeed),
                    createdAt.plusSeconds(2)
            );
            case REVOKED -> applyDecision(
                    confirmed,
                    MemoryDecisionType.REVOKE,
                    null,
                    createdAt.plusSeconds(2)
            );
            case PENDING, REJECTED ->
                    throw new IllegalStateException("目标状态处理分支异常");
        };
    }

    private static MemoryItem applyDecision(
            MemoryItem memory,
            MemoryDecisionType decisionType,
            UUID replacementMemoryId,
            Instant decidedAt
    ) {
        MemoryDecision decision = MemoryDecision.create(
                id(
                        "status-decision-"
                                + memory.memoryId()
                                + "-"
                                + decisionType
                ),
                memory,
                memory.ownerId(),
                decisionType,
                replacementMemoryId,
                "固定Context状态边界Case",
                decidedAt
        );

        return memory.applyDecision(decision);
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