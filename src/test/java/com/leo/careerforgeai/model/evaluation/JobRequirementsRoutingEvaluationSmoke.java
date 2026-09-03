package com.leo.careerforgeai.model.evaluation;

import com.leo.careerforgeai.career.application.requirement.JobRequirementsParseResult;
import com.leo.careerforgeai.career.application.requirement.JobRequirementsParser;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.application.ProviderModelClient;
import com.leo.careerforgeai.model.config.ModelProperties;
import com.leo.careerforgeai.model.config.ModelRoutingProperties;
import com.leo.careerforgeai.model.domain.*;
import com.leo.careerforgeai.model.domain.routing.*;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import com.leo.careerforgeai.model.infrastructure.deepseek.DeepSeekChatClient;
import com.leo.careerforgeai.model.infrastructure.deepseek.stream.DeepSeekSseParser;
import com.leo.careerforgeai.model.infrastructure.kimi.KimiChatClient;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用相同JD和业务解析器比较DeepSeek与Kimi的结构稳定性、事实覆盖率、延迟和Token。
 * @author: Miao Zheng
 * @date: 2026-09-03
 */
@EnabledIfEnvironmentVariable(named = "AI_MODEL_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "MOONSHOT_API_KEY", matches = ".+")
class JobRequirementsRoutingEvaluationSmoke {

    private static final int REPEATS = 5;
    private static final double MIN_SUCCESS_RATE = 0.80;
    private static final double MIN_AVERAGE_COVERAGE = 0.90;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Set<ModelCapability> CAPABILITIES =
            Set.of(ModelCapability.CHAT, ModelCapability.JSON_OBJECT);
    private static final String JD = """
            职位：Java AI应用开发工程师
            岗位要求：
            1. 熟练掌握Java、Spring Boot、MySQL和Redis。
            2. 熟悉LangGraph4j与Function Calling。
            3. 熟悉RAG、Embedding和Elasticsearch。
            4. 具备自动化测试、可观测性和安全工程能力。
            加分项：有MCP项目经验者优先。
            岗位职责：负责AI应用平台的设计与开发。
            """;

    @Test
    void shouldCompareJobRequirementsExtractionAcrossProviders() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            evaluate("deepseek", deepSeek(jsonMapper, httpClient),
                    profile("deepseek-eval", "deepseek",
                            requiredEnv("AI_MODEL_NAME")), jsonMapper,
                    validatorFactory.getValidator());

            ModelRoutingProperties kimiProperties = kimiProperties();
            evaluate("kimi", new KimiChatClient(kimiProperties, jsonMapper, httpClient),
                    kimiProperties.executionRoutes()
                            .get(ModelTaskType.JOB_REQUIREMENTS_EXTRACTION).getFirst(),
                    jsonMapper, validatorFactory.getValidator());
        }
    }

    private void evaluate(String provider, ProviderModelClient client,
                          ModelExecutionProfile profile, JsonMapper jsonMapper,
                          Validator validator) {
        JobRequirementsParser parser =
                new JobRequirementsParser(gateway(client, profile), jsonMapper, validator);
        int successes = 0;
        long totalTokens = 0;
        double totalCoverage = 0;
        List<Long> durations = new ArrayList<>();

        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            try {
                JobRequirementsParseResult result = parser.parseDetailed(JD);
                double coverage = coverage(result.requirements());
                rejectUnsupportedTechnologies(result.requirements());
                successes++;
                totalCoverage += coverage;
                totalTokens += result.modelUsage().totalTokens();
                durations.add(result.modelDurationMs());
                System.out.printf(Locale.ROOT,
                        "task=JOB_REQUIREMENTS_EXTRACTION, provider=%s, model=%s, repeat=%d/%d, status=SUCCEEDED, coverage=%.2f%%, totalTokens=%d, durationMs=%d%n",
                        provider, profile.model(), repeat, REPEATS, coverage * 100,
                        result.modelUsage().totalTokens(), result.modelDurationMs());
            } catch (RuntimeException exception) {
                System.out.printf(Locale.ROOT,
                        "task=JOB_REQUIREMENTS_EXTRACTION, provider=%s, model=%s, repeat=%d/%d, status=FAILED, errorType=%s%n",
                        provider, profile.model(), repeat, REPEATS,
                        exception.getClass().getSimpleName());
            }
        }

        double successRate = successes / (double) REPEATS;
        double averageCoverage = successes == 0 ? 0 : totalCoverage / successes;
        System.out.printf(Locale.ROOT,
                "task=JOB_REQUIREMENTS_EXTRACTION, provider=%s, model=%s, runs=%d, successes=%d, successRate=%.2f%%, averageCoverage=%.2f%%, p50Ms=%d, p95Ms=%d, totalTokens=%d%n",
                provider, profile.model(), REPEATS, successes, successRate * 100,
                averageCoverage * 100, percentile(durations, 0.50),
                percentile(durations, 0.95), totalTokens);

        assertThat(successRate).isGreaterThanOrEqualTo(MIN_SUCCESS_RATE);
        assertThat(averageCoverage).isGreaterThanOrEqualTo(MIN_AVERAGE_COVERAGE);
    }

    private ProviderModelClient deepSeek(JsonMapper jsonMapper,
                                         HttpClient httpClient) {
        return new DeepSeekChatClient(
                new ModelProperties(URI.create(requiredEnv("AI_MODEL_BASE_URL")),
                        requiredEnv("AI_MODEL_API_KEY"),
                        requiredEnv("AI_MODEL_NAME")),
                jsonMapper, new DeepSeekSseParser(jsonMapper), httpClient);
    }

    private ModelRoutingProperties kimiProperties() {
        String model = System.getenv().getOrDefault("KIMI_MODEL", "kimi-k2.6");
        return new ModelRoutingProperties(
                "cp8-job-evaluation-v1",
                Map.of("kimi", new ModelRoutingProperties.Provider(
                        URI.create(System.getenv().getOrDefault(
                                "KIMI_BASE_URL", "https://api.moonshot.cn/v1")),
                        requiredEnv("MOONSHOT_API_KEY"), true)),
                Map.of("kimi-eval", new ModelRoutingProperties.Profile(
                        "kimi", model, CAPABILITIES, ReasoningMode.DISABLED,
                        null, 2_000, TIMEOUT, "evaluation-only", true)),
                Map.of(ModelTaskType.JOB_REQUIREMENTS_EXTRACTION,
                        List.of("kimi-eval")));
    }

    private ModelExecutionProfile profile(String profileId, String provider,
                                          String model) {
        return new ModelExecutionProfile(profileId, provider, model,
                CAPABILITIES, ReasoningMode.DISABLED, null,
                2_000, TIMEOUT, "evaluation-only", true);
    }

    private ModelGateway gateway(ProviderModelClient client,
                                 ModelExecutionProfile profile) {
        return new ModelGateway() {
            @Override
            public ModelResponse chat(ModelTaskType taskType,
                                      ModelRequest request) {
                if (taskType != ModelTaskType.JOB_REQUIREMENTS_EXTRACTION) {
                    throw new IllegalArgumentException("评测任务类型错误");
                }
                return client.chat(profile, request);
            }

            @Override
            public void stream(ModelTaskType taskType, ModelRequest request,
                               Consumer<ModelStreamEvent> consumer) {
                throw new UnsupportedOperationException("当前评测不使用Streaming");
            }
        };
    }

    private double coverage(JobRequirements requirements) {
        int hits = 0;
        hits += hit(normalize(requirements.jobTitle())
                .equals(normalize("Java AI应用开发工程师")));
        hits += hit(contains(requirements.programmingLanguages(), "Java"));
        hits += hit(contains(requirements.backendAndInfrastructureRequirements(), "Spring Boot"));
        hits += hit(contains(requirements.backendAndInfrastructureRequirements(), "MySQL"));
        hits += hit(contains(requirements.backendAndInfrastructureRequirements(), "Redis"));
        hits += hit(contains(requirements.agentRequirements(), "LangGraph4j"));
        hits += hit(contains(requirements.agentRequirements(), "Function Calling"));
        hits += hit(contains(requirements.ragRequirements(), "RAG"));
        hits += hit(contains(requirements.ragRequirements(), "Embedding"));
        hits += hit(contains(requirements.ragRequirements(), "Elasticsearch"));
        hits += hit(contains(requirements.engineeringRequirements(), "测试"));
        hits += hit(contains(requirements.engineeringRequirements(), "可观测性"));
        hits += hit(contains(requirements.engineeringRequirements(), "安全"));
        hits += hit(contains(requirements.bonusQualifications(), "MCP"));
        hits += hit(contains(requirements.responsibilities(), "AI应用平台"));
        return hits / 15.0;
    }

    private void rejectUnsupportedTechnologies(JobRequirements requirements) {
        String output = normalize(String.join("|",
                requirements.programmingLanguages())
                + String.join("|", requirements.backendAndInfrastructureRequirements())
                + String.join("|", requirements.agentRequirements())
                + String.join("|", requirements.ragRequirements())
                + String.join("|", requirements.engineeringRequirements())
                + String.join("|", requirements.bonusQualifications())
                + String.join("|", requirements.responsibilities()));
        if (output.contains("python") || output.contains("kafka")
                || output.contains("kubernetes")) {
            throw new IllegalStateException("模型引入了JD中不存在的技术");
        }
    }

    private boolean contains(List<String> values, String expected) {
        return normalize(String.join("|", values)).contains(normalize(expected));
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-、，,。]", "");
    }

    private int hit(boolean matched) {
        return matched ? 1 : 0;
    }

    private long percentile(List<Long> values, double quantile) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(Math.max(0,
                (int) Math.ceil(sorted.size() * quantile) - 1));
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量：" + name);
        }
        return value;
    }
}