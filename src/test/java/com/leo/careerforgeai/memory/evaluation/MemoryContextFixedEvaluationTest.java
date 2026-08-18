package com.leo.careerforgeai.memory.evaluation;

import com.leo.careerforgeai.memory.application.context.ConversationContext;
import com.leo.careerforgeai.memory.application.context.ConversationContextAssembler;
import com.leo.careerforgeai.memory.application.context.ConversationContextPolicy;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.profile.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用固定数据集评测Memory Context的连续性、隔离、确认、来源和预算指标
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
class MemoryContextFixedEvaluationTest {

    private static final String DATASET_RESOURCE = "memory/evaluation/memory-context-cases.json";
    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final Instant NOW = Instant.parse("2026-08-18T06:00:00Z");

    @Test
    void shouldEvaluateFixedMemoryContextCasesAndReportMetrics() throws Exception {
        EvaluationDataset dataset = loadDataset();
        int taskSuccess = 0;
        int sessionApplicable = 0;
        int sessionCorrect = 0;
        int crossOwnerAttempts = 0;
        int crossOwnerLeaks = 0;
        int inactiveApplicable = 0;
        int inactiveUsage = 0;
        int confirmationApplicable = 0;
        int confirmationCorrect = 0;
        int provenanceTotal = 0;
        int provenanceComplete = 0;
        int budgetRuns = 0;
        int budgetCompliant = 0;
        int totalInjectedMemories = 0;
        int totalContextChars = 0;
        int totalEstimatedTokens = 0;

        for (EvaluationCase evaluationCase : dataset.cases()) {
            ConversationContextPolicy defaults = ConversationContextPolicy.defaults();
            ConversationContextPolicy policy = evaluationCase.maxMemories() == 0
                    ? defaults
                    : new ConversationContextPolicy(
                            defaults.maxRounds(),
                            defaults.maxMessages(),
                            evaluationCase.maxMemories(),
                            defaults.maxContentChars(),
                            defaults.maxEstimatedTokens(),
                            defaults.charsPerEstimatedToken()
                    );
            ConversationContextAssembler assembler = new ConversationContextAssembler(policy);
            UUID sessionId = id(evaluationCase.caseId() + "-session");
            List<ConversationTurn> turns = history(evaluationCase, sessionId);
            long currentSequence = turns.size() + 1L;
            ConversationTurn currentTurn = user(
                    evaluationCase.caseId() + "-current",
                    sessionId,
                    id(evaluationCase.caseId() + "-current-exchange"),
                    ACTOR_A,
                    currentSequence,
                    evaluationCase.currentMessage()
            );
            turns.add(currentTurn);
            List<MemoryItem> memories = memories(evaluationCase);
            ConversationContext context = null;
            IllegalArgumentException failure = null;

            try {
                context = assembler.assemble(currentTurn, turns, memories);
            } catch (IllegalArgumentException exception) {
                failure = exception;
            }

            if ("REJECTED".equals(evaluationCase.expectedOutcome())) {
                crossOwnerAttempts++;
                if (failure == null) crossOwnerLeaks++;
                boolean blocked = failure != null
                        && failure.getMessage().contains("其他用户的数据");
                assertThat(blocked).as(evaluationCase.caseId()).isTrue();
                taskSuccess++;
                continue;
            }

            assertThat(failure).as(evaluationCase.caseId()).isNull();
            assertThat(context).as(evaluationCase.caseId()).isNotNull();
            boolean caseSuccess = context.recentExchanges().size() == evaluationCase.expectedRoundCount()
                    && context.confirmedMemories().size() == evaluationCase.expectedMemoryCount()
                    && context.usage().historyTrimmed() == evaluationCase.expectedHistoryTrimmed()
                    && context.usage().memoriesTrimmed() == evaluationCase.expectedMemoriesTrimmed()
                    && context.currentMessage().equals(evaluationCase.currentMessage());

            if ("MEMORY_PROMPT_INJECTION".equals(evaluationCase.scenario())) {
                caseSuccess = caseSuccess
                        && context.confirmedMemories().stream()
                        .anyMatch(memory -> memory.content().equals(evaluationCase.memoryContent()))
                        && !context.currentMessage().contains(evaluationCase.memoryContent());
            }

            assertThat(caseSuccess).as(evaluationCase.caseId()).isTrue();
            taskSuccess++;

            if (Set.of("SAME_SESSION_HISTORY", "COMPLETE_ROUND_TRIMMING")
                    .contains(evaluationCase.scenario())) {
                sessionApplicable++;
                if (context.recentExchanges().size() == evaluationCase.expectedRoundCount()) {
                    sessionCorrect++;
                }
            }

            List<UUID> inactiveIds = memories.stream()
                    .filter(memory -> memory.status() != MemoryStatus.CONFIRMED)
                    .map(MemoryItem::memoryId)
                    .toList();
            if (!inactiveIds.isEmpty()) {
                inactiveApplicable++;
                boolean inactiveUsed = context.confirmedMemories().stream()
                        .anyMatch(memory -> inactiveIds.contains(memory.memoryId()));
                if (inactiveUsed) inactiveUsage++;
            }

            if (!memories.isEmpty()) {
                confirmationApplicable++;
                boolean onlyConfirmedUsed = context.confirmedMemories().stream()
                        .allMatch(fact -> memories.stream().anyMatch(memory ->
                                memory.memoryId().equals(fact.memoryId())
                                        && memory.status() == MemoryStatus.CONFIRMED));
                if (onlyConfirmedUsed) confirmationCorrect++;
            }

            provenanceTotal += context.confirmedMemories().size();
            provenanceComplete += (int) context.confirmedMemories().stream()
                    .filter(memory -> memory.sourceType() != null)
                    .filter(memory -> memory.sourceId() != null && !memory.sourceId().isBlank())
                    .count();

            budgetRuns++;
            boolean withinBudget = context.usage().roundCount() <= policy.maxRounds()
                    && context.usage().messageCount() <= policy.maxMessages()
                    && context.usage().memoryCount() <= policy.maxMemories()
                    && context.usage().contentChars() <= policy.maxContentChars()
                    && context.usage().estimatedTokens() <= policy.maxEstimatedTokens();
            if (withinBudget) budgetCompliant++;
            totalInjectedMemories += context.usage().memoryCount();
            totalContextChars += context.usage().contentChars();
            totalEstimatedTokens += context.usage().estimatedTokens();
        }

        assertThat(taskSuccess).isEqualTo(9);
        assertThat(sessionCorrect).isEqualTo(sessionApplicable).isEqualTo(2);
        assertThat(crossOwnerAttempts).isEqualTo(1);
        assertThat(crossOwnerLeaks).isZero();
        assertThat(inactiveApplicable).isEqualTo(2);
        assertThat(inactiveUsage).isZero();
        assertThat(confirmationCorrect).isEqualTo(confirmationApplicable).isEqualTo(5);
        assertThat(provenanceComplete).isEqualTo(provenanceTotal).isEqualTo(3);
        assertThat(budgetCompliant).isEqualTo(budgetRuns).isEqualTo(8);

        System.out.printf(
                Locale.ROOT,
                """
                ================ Memory Context Fixed Evaluation ================
                Task Success Rate: %d/%d
                Session Continuity Accuracy: %d/%d
                Cross-owner Leakage Rate: %d/%d
                Inactive Memory Usage Rate: %d/%d
                Confirmation Enforcement Rate: %d/%d
                Provenance Completeness: %d/%d
                Context Budget Compliance: %d/%d
                Average Injected Memories: %.3f (%d runs)
                Average Context Chars: %.3f (%d runs)
                Average Estimated Tokens: %.3f (%d runs)
                Latency p50/p95: N/A (deterministic in-process evaluation)
                =================================================================
                """,
                taskSuccess,
                dataset.cases().size(),
                sessionCorrect,
                sessionApplicable,
                crossOwnerLeaks,
                crossOwnerAttempts,
                inactiveUsage,
                inactiveApplicable,
                confirmationCorrect,
                confirmationApplicable,
                provenanceComplete,
                provenanceTotal,
                budgetCompliant,
                budgetRuns,
                (double) totalInjectedMemories / budgetRuns,
                budgetRuns,
                (double) totalContextChars / budgetRuns,
                budgetRuns,
                (double) totalEstimatedTokens / budgetRuns,
                budgetRuns
        );
    }

    private static EvaluationDataset loadDataset() throws Exception {
        InputStream resource = MemoryContextFixedEvaluationTest.class.getClassLoader()
                .getResourceAsStream(DATASET_RESOURCE);
        if (resource == null) throw new IllegalStateException("固定Memory评测集不存在：" + DATASET_RESOURCE);
        try (resource) {
            return JsonMapper.builder()
                    .build()
                    .readerFor(EvaluationDataset.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(resource);
        }
    }

    private static List<ConversationTurn> history(EvaluationCase evaluationCase, UUID sessionId) {
        List<ConversationTurn> turns = new ArrayList<>();
        ActorId ownerId = new ActorId(evaluationCase.historyOwnerId());
        long sequence = 1;
        for (int index = 0; index < evaluationCase.historyRounds(); index++) {
            UUID exchangeId = id(evaluationCase.caseId() + "-exchange-" + index);
            turns.add(user(
                    evaluationCase.caseId() + "-user-" + index,
                    sessionId,
                    exchangeId,
                    ownerId,
                    sequence++,
                    "固定历史问题-" + index
            ));
            turns.add(assistant(
                    evaluationCase.caseId() + "-assistant-" + index,
                    sessionId,
                    exchangeId,
                    ownerId,
                    sequence++,
                    "固定历史回答-" + index
            ));
        }
        if (evaluationCase.includeIncompleteRound()) {
            turns.add(user(
                    evaluationCase.caseId() + "-incomplete-user",
                    sessionId,
                    id(evaluationCase.caseId() + "-incomplete-exchange"),
                    ownerId,
                    sequence,
                    "没有助手回答的不完整轮次"
            ));
        }
        return turns;
    }

    private static List<MemoryItem> memories(EvaluationCase evaluationCase) {
        List<MemoryItem> memories = new ArrayList<>();
        ActorId ownerId = new ActorId(evaluationCase.memoryOwnerId());
        for (String statusValue : evaluationCase.memoryStatuses()) {
            MemoryStatus status = MemoryStatus.valueOf(statusValue);
            for (int index = 0; index < evaluationCase.memoryCopies(); index++) {
                String seed = evaluationCase.caseId() + "-" + status + "-" + index;
                memories.add(memoryWithStatus(
                        seed,
                        ownerId,
                        status,
                        evaluationCase.memoryContent(),
                        NOW.plusSeconds(index * 10L)
                ));
            }
        }
        return List.copyOf(memories);
    }

    private static MemoryItem memoryWithStatus(
            String seed,
            ActorId ownerId,
            MemoryStatus targetStatus,
            String content,
            Instant createdAt
    ) {
        MemoryItem pending = MemoryItem.createPending(
                id("memory-" + seed),
                ownerId,
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence(seed),
                content,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        id("source-" + seed).toString(),
                        "a".repeat(64)
                ),
                List.of(id("source-" + seed).toString()),
                createdAt
        );
        if (targetStatus == MemoryStatus.PENDING) return pending;
        if (targetStatus == MemoryStatus.REJECTED) {
            return applyDecision(pending, MemoryDecisionType.REJECT, null, createdAt.plusSeconds(1));
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
                    id("replacement-" + seed),
                    createdAt.plusSeconds(2)
            );
            case REVOKED -> applyDecision(
                    confirmed,
                    MemoryDecisionType.REVOKE,
                    null,
                    createdAt.plusSeconds(2)
            );
            case PENDING, REJECTED -> throw new IllegalStateException("Memory状态分支异常");
        };
    }

    private static MemoryItem applyDecision(
            MemoryItem memory,
            MemoryDecisionType decisionType,
            UUID replacementMemoryId,
            Instant decidedAt
    ) {
        MemoryDecision decision = MemoryDecision.create(
                id("decision-" + memory.memoryId() + "-" + decisionType),
                memory,
                memory.ownerId(),
                decisionType,
                replacementMemoryId,
                "固定Memory评测Case",
                decidedAt
        );
        return memory.applyDecision(decision);
    }

    private static ConversationTurn user(
            String seed,
            UUID sessionId,
            UUID exchangeId,
            ActorId ownerId,
            long sequence,
            String content
    ) {
        return ConversationTurn.completedUser(
                id(seed),
                sessionId,
                exchangeId,
                ownerId,
                sequence,
                content,
                NOW.plusSeconds(sequence)
        );
    }

    private static ConversationTurn assistant(
            String seed,
            UUID sessionId,
            UUID exchangeId,
            ActorId ownerId,
            long sequence,
            String content
    ) {
        return ConversationTurn.completedAssistant(
                id(seed),
                sessionId,
                exchangeId,
                ownerId,
                sequence,
                content,
                "evaluation-agent-run-" + sequence,
                NOW.plusSeconds(sequence)
        );
    }

    private static UUID id(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义Memory Context固定评测集
     * @author: Miao Zheng
     * @date: 2026-08-18
     * @param schemaVersion 数据结构版本
     * @param evaluationSetVersion 固定评测集版本
     * @param cases 固定评测Case
     */
    private record EvaluationDataset(
            String schemaVersion,
            String evaluationSetVersion,
            List<EvaluationCase> cases
    ) {
        private EvaluationDataset {
            if (!"memory-context-evaluation-v1".equals(schemaVersion)) {
                throw new IllegalArgumentException("schemaVersion不受支持");
            }
            if (!"careerforge-memory-context-eval-v1".equals(evaluationSetVersion)) {
                throw new IllegalArgumentException("evaluationSetVersion不受支持");
            }
            if (cases == null || cases.size() != 9 || cases.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Memory Context固定评测集必须包含9条Case");
            }
            cases = List.copyOf(cases);
            if (cases.stream().map(EvaluationCase::caseId).distinct().count() != cases.size()) {
                throw new IllegalArgumentException("caseId不能重复");
            }
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义单条Memory Context固定输入和预期结果
     * @author: Miao Zheng
     * @date: 2026-08-18
     * @param caseId Case唯一ID
     * @param scenario 场景类型
     * @param currentMessage 当前用户消息
     * @param historyRounds 完整历史轮数
     * @param includeIncompleteRound 是否加入不完整历史轮次
     * @param historyOwnerId 历史消息owner
     * @param memoryOwnerId Memory owner
     * @param memoryStatuses Memory状态集合
     * @param memoryCopies 每种状态创建数量
     * @param memoryContent Memory正文
     * @param maxMemories 覆盖后的Memory数量预算，0表示使用默认值
     * @param expectedOutcome 预期成功或拒绝
     * @param expectedRoundCount 预期保留历史轮数
     * @param expectedMemoryCount 预期注入Memory数
     * @param expectedHistoryTrimmed 是否预期裁剪历史
     * @param expectedMemoriesTrimmed 是否预期裁剪Memory
     */
    private record EvaluationCase(
            String caseId,
            String scenario,
            String currentMessage,
            int historyRounds,
            boolean includeIncompleteRound,
            String historyOwnerId,
            String memoryOwnerId,
            List<String> memoryStatuses,
            int memoryCopies,
            String memoryContent,
            int maxMemories,
            String expectedOutcome,
            int expectedRoundCount,
            int expectedMemoryCount,
            boolean expectedHistoryTrimmed,
            boolean expectedMemoriesTrimmed
    ) {
        private EvaluationCase {
            if (caseId == null || !caseId.matches("memory-context-eval-[0-9]{3}")) {
                throw new IllegalArgumentException("caseId格式不合法");
            }
            if (scenario == null || scenario.isBlank()) {
                throw new IllegalArgumentException("scenario不能为空");
            }
            if (currentMessage == null || currentMessage.isBlank()) {
                throw new IllegalArgumentException("currentMessage不能为空");
            }
            if (historyRounds < 0 || maxMemories < 0) {
                throw new IllegalArgumentException("评测数量不能小于0");
            }
            if (historyOwnerId == null || memoryOwnerId == null) {
                throw new IllegalArgumentException("owner不能为空");
            }
            if (memoryStatuses == null) {
                throw new IllegalArgumentException("memoryStatuses不能为空");
            }
            memoryStatuses = List.copyOf(memoryStatuses);
            memoryStatuses.forEach(MemoryStatus::valueOf);
            if (memoryStatuses.isEmpty() != (memoryCopies == 0)) {
                throw new IllegalArgumentException("memoryCopies与memoryStatuses不匹配");
            }
            if (!memoryStatuses.isEmpty() && (memoryContent == null || memoryContent.isBlank())) {
                throw new IllegalArgumentException("包含Memory时memoryContent不能为空");
            }
            if (!Set.of("SUCCESS", "REJECTED").contains(expectedOutcome)) {
                throw new IllegalArgumentException("expectedOutcome不受支持");
            }
            if (expectedRoundCount < 0 || expectedMemoryCount < 0) {
                throw new IllegalArgumentException("预期数量不能小于0");
            }
        }
    }
}