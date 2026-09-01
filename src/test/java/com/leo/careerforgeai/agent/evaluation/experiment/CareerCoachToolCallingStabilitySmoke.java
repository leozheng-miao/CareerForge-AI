package com.leo.careerforgeai.agent.evaluation.experiment;

import com.leo.careerforgeai.agent.application.coach.CareerCoachExecutionException;
import com.leo.careerforgeai.agent.application.coach.CareerCoachResult;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.agent.application.tool.career.parse.ParseJobRequirementsTool;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentModelOutcome;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentToolCallTrace;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @program: CareerForge-AI
 * @description: 重复验证原生Career Coach的Tool Calling、模型型工具和最终结构化回答稳定性。
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@SpringBootTest
class CareerCoachToolCallingStabilitySmoke {

    private static final int REPEATS = 5;
    private static final String MESSAGE = """
            请先调用 parse_job_requirements 工具解析下面的岗位JD，再根据工具返回的结构化要求给出学习重点和面试准备建议。
            不需要搜索项目知识库。

            岗位：AI应用开发工程师
            要求：
            1. 熟悉Java 21、Spring Boot和Spring AI。
            2. 理解Tool Calling、Agent Loop、RAG和向量检索。
            3. 能够处理模型超时、重复工具调用和结构化输出校验。
            4. 具备JUnit 5和Mockito自动化测试经验。
            """;

    @Autowired
    private CareerCoachService careerCoachService;

    @MockitoBean
    private KnowledgeEvidenceSearchService evidenceSearchService;

    /** 连续执行固定业务输入，并在全部样本结束后统一判断稳定性。 */
    @Test
    void shouldMeasureNativeCareerCoachToolCallingAndFinalAnswerStability() {
        int successes = 0, failures = 0;
        List<Long> durations = new ArrayList<>();
        List<AgentRunTrace> traces = new ArrayList<>();

        for (int repeat = 1; repeat <= REPEATS; repeat++) {
            long startedAt = System.nanoTime();
            CareerCoachResult result = null;
            AgentRunTrace trace = null;
            RuntimeException failure = null;
            try {
                result = careerCoachService.coach(MESSAGE);
                trace = result.trace();
                validate(result);
                successes++;
            } catch (RuntimeException exception) {
                failure = exception;
                failures++;
                if (trace == null) trace = traceOf(exception);
            }

            long durationMs = elapsedMs(startedAt);
            durations.add(durationMs);
            if (trace != null) traces.add(trace);
            if (failure == null) printSuccess(repeat, result, durationMs);
            else printFailure(repeat, failure, trace, durationMs);
        }

        durations.sort(Long::compareTo);
        long modelExecutions = traces.stream().mapToLong(CareerCoachToolCallingStabilitySmoke::modelExecutions).sum();
        long totalTokens = traces.stream().mapToLong(trace -> trace.totalUsage().totalTokens()).sum();
        System.out.printf(Locale.ROOT,
                "caseId=CP2-CAREER-COACH, totalRuns=%d, successes=%d, failures=%d, successRate=%.2f%%, modelExecutions=%d, p50DurationMs=%d, p95DurationMs=%d, totalTokens=%d%n",
                REPEATS, successes, failures, successes * 100.0 / REPEATS, modelExecutions,
                percentile(durations, 0.50), percentile(durations, 0.95), totalTokens);

        verifyNoInteractions(evidenceSearchService);
        assertThat(failures).isZero();
        assertThat(successes).isEqualTo(REPEATS);
    }

    private static void validate(CareerCoachResult result) {
        AgentRunTrace trace = result.trace();
        require(result.answer().status() == CareerCoachAnswerStatus.ANSWERED, "最终回答状态不是ANSWERED");
        require(!result.answer().answer().isBlank(), "最终回答为空");
        require(result.answer().citedChunkIds().isEmpty(), "未搜索知识库时不允许产生引用");
        require(trace.durationMs() > 0, "Agent耗时必须大于0");
        require(trace.modelCalls().size() == 2, "必须包含Tool Calling和最终回答两轮模型调用");
        require(trace.modelCalls().getFirst().outcome() == AgentModelOutcome.TOOL_CALLS, "第一轮必须请求工具");
        require(trace.modelCalls().getLast().outcome() == AgentModelOutcome.FINAL_ANSWER, "第二轮必须返回最终回答");
        trace.modelCalls().forEach(call -> {
            require(call.modelRequestId() != null && !call.modelRequestId().isBlank(), "模型requestId缺失");
            require(call.usage() != null && call.usage().totalTokens() > 0, "模型Token缺失");
            require(call.durationMs() > 0, "模型耗时必须大于0");
        });

        require(trace.toolCalls().size() == 1, "必须且只能调用一次工具");
        AgentToolCallTrace tool = trace.toolCalls().getFirst();
        require(tool.toolName().equals(ParseJobRequirementsTool.NAME), "调用了非预期工具");
        require(tool.status() == ToolExecutionStatus.SUCCESS, "岗位解析工具执行失败");
        require(tool.resultCount() != null && tool.resultCount() > 0, "岗位解析结果为空");
        require(tool.modelUsage() != null && tool.modelUsage().totalTokens() > 0, "工具内部模型Token缺失");
        require(tool.modelDurationMs() != null && tool.modelDurationMs() > 0, "工具内部模型耗时缺失");
    }

    private static void printSuccess(int repeat, CareerCoachResult result, long durationMs) {
        AgentRunTrace trace = result.trace();
        String requestIds = String.join("|", trace.modelCalls().stream().map(call -> call.modelRequestId()).toList());
        long outerTokens = trace.modelCalls().stream().mapToLong(call -> call.usage().totalTokens()).sum();
        long toolTokens = trace.toolCalls().stream().filter(call -> call.modelUsage() != null)
                .mapToLong(call -> call.modelUsage().totalTokens()).sum();
        System.out.printf(Locale.ROOT,
                "caseId=CP2-CAREER-COACH, repeat=%d/%d, status=SUCCEEDED, runId=%s, modelRequestIds=%s, modelCalls=%d, toolCalls=%d, modelExecutions=%d, outerTokens=%d, toolModelTokens=%d, totalTokens=%d, answerChars=%d, answerHash=%s, durationMs=%d%n",
                repeat, REPEATS, trace.runId(), requestIds, trace.modelCalls().size(), trace.toolCalls().size(),
                modelExecutions(trace), outerTokens, toolTokens, trace.totalUsage().totalTokens(),
                result.answer().answer().length(), sha256(result.answer().answer()), durationMs);
    }

    private static void printFailure(int repeat, RuntimeException failure,
                                     AgentRunTrace trace, long durationMs) {
        String runId = trace == null ? "UNKNOWN" : trace.runId();
        long executions = trace == null ? 0 : modelExecutions(trace);
        long tokens = trace == null ? 0 : trace.totalUsage().totalTokens();
        String stage = "UNKNOWN", reason = "UNKNOWN", fieldPath = "UNKNOWN";
        Integer outputChars = null;
        String outputHash = null;

        if (failure instanceof CareerCoachFinalAnswerException exception) {
            stage = String.valueOf(exception.getFailureStage());
            reason = String.valueOf(exception.getFailureReason());
            fieldPath = String.valueOf(exception.getFieldPath());
            outputChars = exception.getOutputChars();
            outputHash = exception.getOutputSha256();
        }

        System.out.printf(Locale.ROOT,
                "caseId=CP2-CAREER-COACH, repeat=%d/%d, status=FAILED, runId=%s, failureType=%s, failureStage=%s, failureReason=%s, fieldPath=%s, modelExecutions=%d, totalTokens=%d, outputChars=%s, outputHash=%s, durationMs=%d%n",
                repeat, REPEATS, runId, failureType(failure), stage, reason, fieldPath,
                executions, tokens, outputChars, outputHash, durationMs);
    }

    private static AgentRunTrace traceOf(RuntimeException exception) {
        if (exception instanceof CareerCoachExecutionException failure) return failure.getTrace();
        if (exception instanceof CareerCoachFinalAnswerException failure) return failure.getTrace();
        return null;
    }

    private static String failureType(RuntimeException exception) {
        if (exception instanceof CareerCoachExecutionException failure) {
            return failure.getRunStatus() + "_" + failure.getTerminationReason();
        }
        if (exception instanceof CareerCoachFinalAnswerException failure) return "FINAL_" + failure.getErrorType();
        return "RUNTIME_" + exception.getClass().getSimpleName();
    }

    private static long modelExecutions(AgentRunTrace trace) {
        return trace.modelCalls().size() + trace.toolCalls().stream().filter(call -> call.modelUsage() != null).count();
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static long percentile(List<Long> sortedValues, double percentile) {
        int index = Math.max(0, Math.min(sortedValues.size() - 1,
                (int) Math.ceil(sortedValues.size() * percentile) - 1));
        return sortedValues.get(index);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}