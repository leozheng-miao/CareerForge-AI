package com.leo.careerforgeai.memory.evaluation.experiment;

import com.leo.careerforgeai.agent.application.coach.CareerCoachResult;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import com.leo.careerforgeai.memory.application.context.ConversationContext;
import com.leo.careerforgeai.memory.application.context.ConversationContextAssembler;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @program: CareerForge-AI
 * @description: 使用真实模型对照验证CONFIRMED Memory注入是否带来可重复的回答业务收益
 * @author: Miao Zheng
 * @date: 2026-08-15
 **/
@SpringBootTest(properties = "careerforge.persistence.enabled=false")
class MemoryContextBusinessBenefitSmoke {

    private static final String CASE_ID = "memory-context-benefit-001";
    private static final int RUNS_PER_GROUP = 3;
    private static final ActorId ACTOR_ID =
            new ActorId("memory-benefit-smoke-owner");
    private static final UUID SESSION_ID = id("memory-benefit-session");
    private static final Instant NOW =
            Instant.parse("2026-08-15T00:00:00Z");
    private static final String USER_MESSAGE = """
            请只根据你获得的已确认个人背景回答：
            我每周能投入多少学习时间，应该分别安排在哪些时段？
            如果没有相应个人背景，请明确说明无法确认，不要猜测。
            不要调用工具。
            """;
    private static final String MEMORY_CONTENT =
            "每周最多投入七小时，只能在周六上午和周日下午学习";
    private static final String SOURCE_ID =
            "benefit-source-weekly-schedule";

    @Autowired
    private CareerCoachService careerCoachService;

    @Autowired
    private ConversationContextAssembler contextAssembler;

    @MockitoBean
    private KnowledgeEvidenceSearchService evidenceSearchService;

    /**
     * @program: CareerForge-AI
     * @description: 固定运行三组配对请求并验证Memory事实使用率、基线泄漏率、Token和Context成本
     * @author: Miao Zheng
     * @date: 2026-08-15
     **/
    @Test
    void shouldShowRepeatableAnswerBenefitFromConfirmedMemory() {
        ConversationTurn currentUser = ConversationTurn.completedUser(
                id("memory-benefit-user-turn"),
                SESSION_ID,
                id("memory-benefit-exchange"),
                ACTOR_ID,
                1,
                USER_MESSAGE,
                NOW
        );

        MemoryItem confirmedMemory = confirmedMemory();

        ConversationContext withoutMemory = contextAssembler.assemble(
                currentUser,
                List.of(currentUser),
                List.of()
        );

        ConversationContext withMemory = contextAssembler.assemble(
                currentUser,
                List.of(currentUser),
                List.of(confirmedMemory)
        );

        assertThat(withoutMemory.usage().memoryCount()).isZero();
        assertThat(withMemory.usage().memoryCount()).isEqualTo(1);
        assertThat(withMemory.confirmedMemories().getFirst().sourceId())
                .isEqualTo(SOURCE_ID);
        assertThat(withMemory.usage().contentChars())
                .isGreaterThan(withoutMemory.usage().contentChars());
        assertThat(withMemory.usage().estimatedTokens())
                .isGreaterThan(withoutMemory.usage().estimatedTokens());

        int baselineFactUses = 0;
        int memoryFactUses = 0;
        long baselineInputTokens = 0;
        long memoryInputTokens = 0;
        long baselineDurationMs = 0;
        long memoryDurationMs = 0;

        for (int run = 1; run <= RUNS_PER_GROUP; run++) {
            CareerCoachResult baselineResult =
                    careerCoachService.coachWithContext(withoutMemory);
            CareerCoachResult memoryResult =
                    careerCoachService.coachWithContext(withMemory);

            assertSuccessfulDirectAnswer(baselineResult);
            assertSuccessfulDirectAnswer(memoryResult);

            boolean baselineUsedFact =
                    usesConfirmedSchedule(baselineResult.answer().answer());
            boolean memoryUsedFact =
                    usesConfirmedSchedule(memoryResult.answer().answer());

            if (baselineUsedFact) {
                baselineFactUses++;
            }
            if (memoryUsedFact) {
                memoryFactUses++;
            }

            baselineInputTokens +=
                    baselineResult.trace().totalUsage().inputTokens();
            memoryInputTokens +=
                    memoryResult.trace().totalUsage().inputTokens();
            baselineDurationMs += baselineResult.trace().durationMs();
            memoryDurationMs += memoryResult.trace().durationMs();

            System.out.printf(
                    "caseId=%s, run=%d, group=WITHOUT_MEMORY, usedConfirmedFact=%s, inputTokens=%d, totalTokens=%d, durationMs=%d, answer=%s%n",
                    CASE_ID,
                    run,
                    baselineUsedFact,
                    baselineResult.trace().totalUsage().inputTokens(),
                    baselineResult.trace().totalUsage().totalTokens(),
                    baselineResult.trace().durationMs(),
                    baselineResult.answer().answer()
            );
            System.out.printf(
                    "caseId=%s, run=%d, group=WITH_MEMORY, usedConfirmedFact=%s, inputTokens=%d, totalTokens=%d, durationMs=%d, answer=%s%n",
                    CASE_ID,
                    run,
                    memoryUsedFact,
                    memoryResult.trace().totalUsage().inputTokens(),
                    memoryResult.trace().totalUsage().totalTokens(),
                    memoryResult.trace().durationMs(),
                    memoryResult.answer().answer()
            );
        }

        System.out.printf(
                "caseId=%s, baselineFactUses=%d/%d, memoryFactUses=%d/%d, contextMemoryCount=%d, contextCharsWithoutMemory=%d, contextCharsWithMemory=%d, estimatedTokensWithoutMemory=%d, estimatedTokensWithMemory=%d, baselineInputTokens=%d, memoryInputTokens=%d, baselineDurationMs=%d, memoryDurationMs=%d, sourceIds=%s%n",
                CASE_ID,
                baselineFactUses,
                RUNS_PER_GROUP,
                memoryFactUses,
                RUNS_PER_GROUP,
                withMemory.usage().memoryCount(),
                withoutMemory.usage().contentChars(),
                withMemory.usage().contentChars(),
                withoutMemory.usage().estimatedTokens(),
                withMemory.usage().estimatedTokens(),
                baselineInputTokens,
                memoryInputTokens,
                baselineDurationMs,
                memoryDurationMs,
                withMemory.confirmedMemories()
                        .stream()
                        .map(ConversationContext.ConfirmedMemoryFact::sourceId)
                        .toList()
        );

        assertThat(baselineFactUses).isZero();
        assertThat(memoryFactUses).isEqualTo(RUNS_PER_GROUP);
        assertThat(memoryInputTokens).isGreaterThan(baselineInputTokens);
        verifyNoInteractions(evidenceSearchService);
    }

    private static void assertSuccessfulDirectAnswer(
            CareerCoachResult result
    ) {
        assertThat(result.answer().status())
                .isEqualTo(CareerCoachAnswerStatus.ANSWERED);
        assertThat(result.answer().answer()).isNotBlank();
        assertThat(result.answer().citedChunkIds()).isEmpty();
        assertThat(result.trace().modelCalls()).hasSize(1);
        assertThat(result.trace().toolCalls()).isEmpty();
        assertThat(result.trace().totalUsage().inputTokens()).isPositive();
        assertThat(result.trace().totalUsage().totalTokens()).isPositive();
    }

    private static boolean usesConfirmedSchedule(String answer) {
        String normalized = answer
                .replaceAll("\\s+", "")
                .toLowerCase();

        boolean containsHours =
                normalized.contains("七小时")
                        || normalized.contains("7小时");

        return containsHours
                && normalized.contains("周六上午")
                && normalized.contains("周日下午");
    }

    private static MemoryItem confirmedMemory() {
        MemoryItem pending = MemoryItem.createPending(
                id("memory-benefit-item"),
                ACTOR_ID,
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(
                        TimeConstraintKey.WEEKLY_HOURS
                ),
                MEMORY_CONTENT,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        SOURCE_ID,
                        "a".repeat(64)
                ),
                List.of(SOURCE_ID),
                NOW
        );

        MemoryDecision confirmation = MemoryDecision.create(
                id("memory-benefit-confirmation"),
                pending,
                ACTOR_ID,
                MemoryDecisionType.CONFIRM,
                null,
                "固定真实收益对照Case",
                NOW.plusSeconds(1)
        );

        return pending.applyDecision(confirmation);
    }

    private static UUID id(String seed) {
        return UUID.nameUUIDFromBytes(
                seed.getBytes(StandardCharsets.UTF_8)
        );
    }
}