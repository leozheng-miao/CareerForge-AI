package com.leo.careerforgeai.model.evaluation;

import com.leo.careerforgeai.career.application.skillgap.DeterministicSkillGapMatcher;
import com.leo.careerforgeai.career.application.training.*;
import com.leo.careerforgeai.career.domain.*;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.memory.application.extraction.MemoryCandidateExtractor;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionException;
import com.leo.careerforgeai.memory.application.extraction.dto.model.*;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnRole;
import com.leo.careerforgeai.memory.domain.profile.*;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.config.ModelProperties;
import com.leo.careerforgeai.model.domain.*;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionException;
import com.leo.careerforgeai.model.infrastructure.deepseek.DeepSeekChatClient;
import com.leo.careerforgeai.model.infrastructure.deepseek.stream.DeepSeekSseParser;
import com.leo.careerforgeai.shared.actor.ActorId;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用固定输入重复验证Memory提取和训练计划生成的真实DeepSeek结构化稳定性
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
class StructuredBusinessOutputStabilitySmoke {

    private static final int REPEATS = 5;
    private static final ActorId ACTOR = new ActorId("cp2-structured-smoke");
    private static final UUID TURN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ROLE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final ModelGateway realGateway = new DeepSeekChatClient(
            new ModelProperties(
                    URI.create(requiredEnv("AI_MODEL_BASE_URL")),
                    requiredEnv("AI_MODEL_API_KEY"),
                    requiredEnv("AI_MODEL_NAME")
            ),
            jsonMapper,
            new DeepSeekSseParser(jsonMapper),
            HttpClient.newHttpClient()
    );

    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    /** 固定五次验证Memory结构、来源引用和一次受控修复。 */
    @Test
    void shouldMeasureMemoryExtractionStability() {
        List<ModelResponse> calls = new ArrayList<>();
        MemoryCandidateExtractor extractor = new MemoryCandidateExtractor(
                capturingGateway(calls), jsonMapper, validatorFactory.getValidator()
        );
        int successes = 0, failures = 0, repairs = 0, providerCalls = 0;
        long totalTokens = 0;
        List<Long> durations = new ArrayList<>();

        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            long started = System.nanoTime();
            try {
                MemoryExtractionResult result = extractor.extract(memoryInput());
                if (result.candidates().isEmpty()) throw new IllegalStateException("Memory候选为空");
                successes++;
                repairs += Math.max(0, result.modelCallCount() - 1);
                durations.add(result.modelDurationMs());
                System.out.printf(
                        Locale.ROOT,
                        "caseId=CP2-MEMORY, repeat=%d/%d, status=SUCCEEDED, requestId=%s, modelCallCount=%d, candidates=%d, totalTokens=%d, durationMs=%d%n",
                        repeat, REPEATS, result.modelRequestId(), result.modelCallCount(),
                        result.candidates().size(), result.modelUsage().totalTokens(), result.modelDurationMs()
                );
            } catch (RuntimeException exception) {
                failures++;
                printFailure("CP2-MEMORY", repeat, exception, elapsedMillis(started));
            } finally {
                printProviderCalls("CP2-MEMORY", repeat, calls);
                providerCalls += calls.size();
                totalTokens += totalTokens(calls);
                calls.clear();
            }
        }

        printSummary("CP2-MEMORY", successes, failures, repairs, providerCalls, totalTokens, durations);
        assertThat(failures).isZero();
    }

    /** 固定五次验证训练计划JSON、Gap、资源、时间预算和事实边界。 */
    @Test
    void shouldMeasureTrainingPlanStability() {
        List<ModelResponse> calls = new ArrayList<>();
        TrainingPlanGenerator generator = new TrainingPlanGenerator(
                capturingGateway(calls), jsonMapper, validatorFactory.getValidator(), Clock.systemUTC()
        );
        int successes = 0, failures = 0, providerCalls = 0;
        long totalTokens = 0;
        List<Long> durations = new ArrayList<>();

        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            long started = System.nanoTime();
            try {
                TrainingPlanGenerator.GeneratedPlan result = generator.generate(trainingInput());
                successes++;
                durations.add(result.modelDurationMs());
                System.out.printf(
                        Locale.ROOT,
                        "caseId=CP2-TRAINING, repeat=%d/%d, status=SUCCEEDED, requestId=%s, items=%d, totalTokens=%d, durationMs=%d%n",
                        repeat, REPEATS, result.modelRequestId(), result.items().size(),
                        result.modelUsage().totalTokens(), result.modelDurationMs()
                );
            } catch (RuntimeException exception) {
                failures++;
                printFailure("CP2-TRAINING", repeat, exception, elapsedMillis(started));
            } finally {
                printProviderCalls("CP2-TRAINING", repeat, calls);
                providerCalls += calls.size();
                totalTokens += totalTokens(calls);
                calls.clear();
            }
        }

        printSummary("CP2-TRAINING", successes, failures, 0, providerCalls, totalTokens, durations);
        assertThat(failures).isZero();
    }

    private ModelGateway capturingGateway(List<ModelResponse> calls) {
        return new ModelGateway() {
            @Override
            public ModelResponse chat(ModelRequest request) {
                ModelResponse response = realGateway.chat(request);
                calls.add(response);
                return response;
            }

            @Override
            public void stream(ModelRequest request, Consumer<ModelStreamEvent> consumer) {
                realGateway.stream(request, consumer);
            }
        };
    }

    private static List<MemoryExtractionTurnInput> memoryInput() {
        return List.of(new MemoryExtractionTurnInput(
                TURN_ID,
                ConversationTurnRole.USER,
                "我已确认每周可以投入10小时学习Java、Spring Boot和AI Agent开发。"
        ));
    }

    private static TrainingPlanGenerationInputReader.FixedInput trainingInput() {
        TargetRole targetRole = TargetRole.createConfirmed(
                TARGET_ROLE_ID, ACTOR, 1, "job-description-1", "a".repeat(64),
                "job-requirements-parser-v1", "job-requirements-prompt-v1",
                new JobRequirements(
                        "Java AI应用开发工程师",
                        List.of("Java"), List.of("Spring Boot"), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of()
                ),
                NOW.minusSeconds(120)
        );
        SkillGapSnapshot snapshot = SkillGapSnapshot.create(
                SNAPSHOT_ID, ACTOR, TARGET_ROLE_ID, 1, 0,
                DeterministicSkillGapMatcher.ALGORITHM_VERSION,
                List.of(new SkillGapSnapshot.GapItem(
                        GAP_ITEM_ID, "programmingLanguages[0]", "Java",
                        SkillGapSnapshot.GapStatus.MISSING, List.of(), "当前画像中没有Java证据"
                )),
                NOW.minusSeconds(90)
        );
        return new TrainingPlanGenerationInputReader.FixedInput(
                TrainingPlanGenerationInputReader.INPUT_POLICY_VERSION,
                ACTOR, targetRole, snapshot, new ConfirmedSkillProfile(ACTOR, 0, List.of()),
                600, List.of(confirmedWeeklyHours()),
                List.of(new TrainingPlanGenerationInputReader.ControlledResource(
                        "careerforge", "document-1", "Java Agent训练资料",
                        KnowledgeDocumentType.JOB_DESCRIPTION, "d".repeat(64)
                ))
        );
    }

    private static MemoryItem confirmedWeeklyHours() {
        MemoryItem pending = MemoryItem.createPending(
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                ACTOR, MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                "我每周可以学习10小时",
                new MemorySource(MemorySourceType.CONVERSATION_TURN, "turn-weekly-hours", "c".repeat(64)),
                List.of("turn-weekly-hours"), NOW.minusSeconds(60)
        );
        return pending.applyDecision(MemoryDecision.create(
                UUID.fromString("60000000-0000-0000-0000-000000000001"),
                pending, ACTOR, MemoryDecisionType.CONFIRM, null,
                "用户确认每周时间", NOW.minusSeconds(50)
        ));
    }

    private static void printProviderCalls(String caseId, int repeat, List<ModelResponse> calls) {
        for (int index = 0; index < calls.size(); index++) {
            ModelResponse response = calls.get(index);
            System.out.printf(
                    Locale.ROOT,
                    "caseId=%s, repeat=%d/%d, providerCall=%d, requestId=%s, model=%s, finishReason=stop, outputChars=%d, outputHash=%s, inputTokens=%d, outputTokens=%d, totalTokens=%d%n",
                    caseId, repeat, REPEATS, index + 1, response.requestId(), response.model(),
                    response.content().length(), sha256(response.content()), response.usage().inputTokens(),
                    response.usage().outputTokens(), response.usage().totalTokens()
            );
        }
    }

    private static void printFailure(String caseId, int repeat, RuntimeException exception, long durationMs) {
        Throwable current = exception;
        while (current != null && !(current instanceof ModelCompletionException)) current = current.getCause();
        if (current instanceof ModelCompletionException completion) {
            System.out.printf(
                    Locale.ROOT,
                    "caseId=%s, repeat=%d/%d, status=FAILED, errorType=%s, completionStatus=%s, finishReason=%s, requestId=%s, outputChars=%d, outputHash=%s, totalTokens=%s, durationMs=%d%n",
                    caseId, repeat, REPEATS, completion.getErrorType(), completion.completionStatus(),
                    completion.providerFinishReason(), completion.providerRequestId(), completion.outputChars(),
                    completion.outputSha256(), completion.usage() == null ? "UNKNOWN" : completion.usage().totalTokens(),
                    durationMs
            );
            return;
        }
        System.out.printf(
                Locale.ROOT,
                "caseId=%s, repeat=%d/%d, status=FAILED, errorType=%s, durationMs=%d%n",
                caseId, repeat, REPEATS, failureType(exception), durationMs
        );
    }

    private static String failureType(RuntimeException exception) {
        if (exception instanceof MemoryExtractionException value) {
            return value.getErrorType() + "/" + value.getFailureStage();
        }
        if (exception instanceof TrainingPlanGenerationException value) return value.getErrorType().name();
        if (exception instanceof ModelException value) return value.getErrorType().name();
        return exception.getClass().getSimpleName();
    }

    private static void printSummary(
            String caseId, int successes, int failures, int repairs,
            int providerCalls, long totalTokens, List<Long> durations
    ) {
        System.out.printf(
                Locale.ROOT,
                "caseId=%s, totalRuns=%d, successes=%d, failures=%d, repairs=%d, providerCalls=%d, successRate=%.2f%%, p50DurationMs=%d, p95DurationMs=%d, totalTokens=%d%n",
                caseId, REPEATS, successes, failures, repairs, providerCalls,
                successes * 100.0 / REPEATS, percentile(durations, 0.50), percentile(durations, 0.95), totalTokens
        );
    }

    private static long totalTokens(List<ModelResponse> calls) {
        return calls.stream().filter(response -> response.usage() != null)
                .mapToLong(response -> response.usage().totalTokens()).sum();
    }

    private static long percentile(List<Long> values, double quantile) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * quantile) - 1);
        return sorted.get(index);
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }
}