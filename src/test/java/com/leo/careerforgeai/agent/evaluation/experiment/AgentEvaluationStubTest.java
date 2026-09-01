package com.leo.careerforgeai.agent.evaluation.experiment;

import com.leo.careerforgeai.agent.application.coach.CareerCoachExecutionException;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerValidator;
import com.leo.careerforgeai.agent.application.coach.CareerCoachResult;
import com.leo.careerforgeai.agent.application.coach.CareerCoachScopeProvider;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
import com.leo.careerforgeai.agent.application.loop.AgentLoop;
import com.leo.careerforgeai.agent.application.loop.HeuristicAgentTokenEstimator;
import com.leo.careerforgeai.agent.application.loop.ToolCallFingerprintService;
import com.leo.careerforgeai.agent.application.tool.AgentTool;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.application.tool.career.search.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.application.tool.career.search.CareerMaterialScopePolicy;
import com.leo.careerforgeai.agent.application.tool.career.parse.ParseJobRequirementsTool;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentToolCallTrace;
import com.leo.careerforgeai.agent.domain.tool.AgentToolOutput;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.domain.tool.career.parse.ParseJobRequirementsInput;
import com.leo.careerforgeai.agent.domain.tool.career.parse.ParseJobRequirementsOutput;
import com.leo.careerforgeai.agent.domain.tool.career.search.CareerMaterialEvidence;
import com.leo.careerforgeai.agent.domain.tool.career.search.SearchCareerMaterialsErrorType;
import com.leo.careerforgeai.agent.domain.tool.career.search.SearchCareerMaterialsInput;
import com.leo.careerforgeai.agent.domain.tool.career.search.SearchCareerMaterialsOutput;
import com.leo.careerforgeai.agent.evaluation.dataset.AgentEvaluationDataset;
import com.leo.careerforgeai.agent.evaluation.dataset.AgentEvaluationDatasetLoader;
import com.leo.careerforgeai.agent.evaluation.execution.RecordingToolCallingGateway;
import com.leo.careerforgeai.agent.evaluation.metrics.AgentCaseMeasurement;
import com.leo.careerforgeai.agent.evaluation.metrics.AgentCaseMetricsCalculator;
import com.leo.careerforgeai.agent.evaluation.metrics.AgentMetricsAggregator;
import com.leo.careerforgeai.career.application.requirement.JobRequirementsParser;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.FinalAnswerResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallsResult;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachStructuredOutputRepairer;

/**
 * @program: CareerForge-AI
 * @description: 一次执行全部固定Agent Stub Case并输出逐Case结果和聚合指标。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
class AgentEvaluationStubTest {

    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");
    private static final String MODEL = "careerforge-agent-stub";
    private static final String INTERVIEW_CHUNK_ID = "1".repeat(64);
    private static final String JOB_CHUNK_ID = "2".repeat(64);
    private static final ModelUsage TOOL_DECISION_USAGE = new ModelUsage(40, 10, 50);
    private static final ModelUsage FINAL_ANSWER_USAGE = new ModelUsage(80, 20, 100);
    private static final ModelUsage PARSE_MODEL_USAGE = new ModelUsage(120, 40, 160);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ToolContract<ParseJobRequirementsInput, ParseJobRequirementsOutput> parseContract =
            new ParseJobRequirementsTool(mock(JobRequirementsParser.class)).contract();
    private final ToolContract<SearchCareerMaterialsInput, SearchCareerMaterialsOutput> searchContract =
            new SearchCareerMaterialsTool(
                    mock(KnowledgeEvidenceSearchService.class),
                    new CareerMaterialScopePolicy()
            ).contract();

    @AfterEach
    void closeResources() {
        executorService.shutdownNow();
        validatorFactory.close();
    }

    @Test
    void shouldEvaluateAllFixedAgentCasesAndPrintMetrics() {
        AgentEvaluationDataset dataset = new AgentEvaluationDatasetLoader(jsonMapper).load();
        List<CaseRunResult> results = new ArrayList<>();

        for (AgentEvaluationDataset.EvaluationCase evaluationCase : dataset.cases()) {
            results.add(runCase(evaluationCase, scenarioFor(evaluationCase.caseId())));
        }

        List<AgentCaseMetricsCalculator.AgentCaseMetrics> metrics = results.stream()
                .map(CaseRunResult::metrics)
                .toList();
        AgentMetricsAggregator.AgentEvaluationSummary summary =
                new AgentMetricsAggregator().aggregate(metrics);

        printReport(results, summary);

        assertThat(results).hasSize(13);
        assertThat(results).allSatisfy(result ->
                assertThat(result.metrics().taskSucceeded())
                        .as(result.measurement().caseId())
                        .isTrue()
        );
        assertThat(summary.distinctCaseCount()).isEqualTo(13);
        assertThat(summary.caseRunCount()).isEqualTo(13);
        assertRatio(summary.requiredToolRecall(), 12, 12);
        assertRatio(summary.unnecessaryToolCallRate(), 1, 15);
        assertRatio(summary.argumentValidRate(), 14, 15);
        assertRatio(summary.toolSequenceAccuracy(), 9, 9);
        assertRatio(summary.taskSuccessRate(), 13, 13);
        assertRatio(summary.citationLegalRate(), 4, 4);
        assertRatio(summary.loopTerminationRate(), 13, 13);
        assertRatio(summary.toolFailureRecoveryRate(), 2, 2);
        assertThat(summary.totalOuterModelTokens()).isEqualTo(1_900);
        assertThat(summary.totalToolModelTokens()).isEqualTo(640);
    }

    private CaseRunResult runCase(
            AgentEvaluationDataset.EvaluationCase evaluationCase,
            StubScenario scenario
    ) {
        QueueToolCallingGateway scriptedGateway =
                new QueueToolCallingGateway(scenario.modelResults());
        RecordingToolCallingGateway recordingGateway =
                new RecordingToolCallingGateway(scriptedGateway);
        CareerCoachService careerCoachService = careerCoachService(
                recordingGateway,
                scenarioTools(scenario)
        );

        AgentRunTrace trace;
        AgentCaseMeasurement.FinalAnswerOutcome finalAnswerOutcome;
        CareerCoachAnswerStatus answerStatus;
        List<String> citedChunkIds;

        try {
            CareerCoachResult result =
                    careerCoachService.coach(evaluationCase.userMessage());
            trace = result.trace();
            finalAnswerOutcome = AgentCaseMeasurement.FinalAnswerOutcome.VALID;
            answerStatus = result.answer().status();
            citedChunkIds = result.answer().citedChunkIds();
        } catch (CareerCoachExecutionException exception) {
            trace = exception.getTrace();
            finalAnswerOutcome =
                    AgentCaseMeasurement.FinalAnswerOutcome.NOT_PRODUCED;
            answerStatus = null;
            citedChunkIds = List.of();
        }

        if (!scriptedGateway.isExhausted()) {
            throw new IllegalStateException(
                    evaluationCase.caseId() + "仍有未消费的模型脚本"
            );
        }

        AgentCaseMeasurement measurement = new AgentCaseMeasurement(
                evaluationCase.caseId(),
                1,
                AgentCaseMeasurement.ExecutionMode.STUB,
                trace.runId(),
                toolMeasurements(
                        recordingGateway.recordedToolCalls(),
                        trace.toolCalls()
                ),
                trace.status(),
                trace.terminationReason(),
                finalAnswerOutcome,
                answerStatus,
                citedChunkIds,
                scenario.allowedCitationChunkIds(),
                trace.modelCalls().size(),
                trace.modelCalls().stream()
                        .filter(modelCall -> modelCall.usage() != null)
                        .mapToLong(modelCall -> modelCall.usage().totalTokens())
                        .sum(),
                trace.toolCalls().stream()
                        .filter(toolCall -> toolCall.modelUsage() != null)
                        .mapToLong(toolCall -> toolCall.modelUsage().totalTokens())
                        .sum(),
                trace.durationMs()
        );
        AgentCaseMetricsCalculator.AgentCaseMetrics metrics =
                new AgentCaseMetricsCalculator().calculate(
                        evaluationCase,
                        measurement
                );

        return new CaseRunResult(evaluationCase, measurement, metrics);
    }

    private List<AgentCaseMeasurement.ToolCallMeasurement> toolMeasurements(
            List<RecordingToolCallingGateway.RecordedToolCall> recordedCalls,
            List<AgentToolCallTrace> traces
    ) {
        Map<String, AgentToolCallTrace> tracesById = new HashMap<>();
        for (AgentToolCallTrace trace : traces) {
            tracesById.put(trace.toolCallId(), trace);
        }

        Set<String> recordedIds = new HashSet<>();
        for (RecordingToolCallingGateway.RecordedToolCall recordedCall : recordedCalls) {
            recordedIds.add(recordedCall.toolCallId());
        }
        if (!recordedIds.containsAll(tracesById.keySet())) {
            throw new IllegalStateException(
                    "Tool Trace包含模型请求记录中不存在的Tool Call ID"
            );
        }

        List<AgentCaseMeasurement.ToolCallMeasurement> measurements =
                new ArrayList<>();

        for (int index = 0; index < recordedCalls.size(); index++) {
            RecordingToolCallingGateway.RecordedToolCall recordedCall =
                    recordedCalls.get(index);
            AgentToolCallTrace trace =
                    tracesById.get(recordedCall.toolCallId());

            if (trace == null) {
                measurements.add(
                        new AgentCaseMeasurement.ToolCallMeasurement(
                                recordedCall.sequence(),
                                recordedCall.modelIteration(),
                                recordedCall.toolCallId(),
                                recordedCall.toolName(),
                                rejectedArgumentsWerePreviouslyValid(
                                        index,
                                        recordedCalls,
                                        tracesById
                                ),
                                AgentCaseMeasurement.ToolAttemptOutcome
                                        .REJECTED_BY_LOOP,
                                null
                        )
                );
                continue;
            }

            measurements.add(
                    new AgentCaseMeasurement.ToolCallMeasurement(
                            recordedCall.sequence(),
                            recordedCall.modelIteration(),
                            recordedCall.toolCallId(),
                            recordedCall.toolName(),
                            argumentsValid(trace),
                            trace.status() == ToolExecutionStatus.SUCCESS
                                    ? AgentCaseMeasurement.ToolAttemptOutcome.SUCCESS
                                    : AgentCaseMeasurement.ToolAttemptOutcome.FAILURE,
                            trace.errorType()
                    )
            );
        }

        return List.copyOf(measurements);
    }

    private boolean rejectedArgumentsWerePreviouslyValid(
            int currentIndex,
            List<RecordingToolCallingGateway.RecordedToolCall> recordedCalls,
            Map<String, AgentToolCallTrace> tracesById
    ) {
        RecordingToolCallingGateway.RecordedToolCall rejected =
                recordedCalls.get(currentIndex);

        for (int index = 0; index < currentIndex; index++) {
            RecordingToolCallingGateway.RecordedToolCall previous =
                    recordedCalls.get(index);
            AgentToolCallTrace previousTrace =
                    tracesById.get(previous.toolCallId());

            if (previous.toolName().equals(rejected.toolName())
                    && previous.argumentsJson()
                    .equals(rejected.argumentsJson())
                    && previousTrace != null
                    && argumentsValid(previousTrace)) {
                return true;
            }
        }
        return false;
    }

    private boolean argumentsValid(AgentToolCallTrace trace) {
        return trace.errorType() != ToolExecutionErrorType.INVALID_ARGUMENTS
                && trace.errorType()
                != ToolExecutionErrorType.VALIDATION_FAILED;
    }

    private List<AgentTool<?, ?>> scenarioTools(StubScenario scenario) {
        return List.of(
                new FixedTool<>(
                        parseContract,
                        scenario.parseToolOutput()
                ),
                new FixedTool<>(
                        searchContract,
                        scenario.searchToolOutput()
                )
        );
    }

    private StubScenario scenarioFor(String caseId) {
        AgentToolOutput<ParseJobRequirementsOutput> parseSuccess =
                parseSuccessOutput();
        AgentToolOutput<SearchCareerMaterialsOutput> interviewSuccess =
                searchSuccessOutput(
                        "fixture-interview",
                        INTERVIEW_CHUNK_ID,
                        "interview-document",
                        "Java并发面经.md",
                        KnowledgeDocumentType.INTERVIEW_EXPERIENCE
                );
        AgentToolOutput<SearchCareerMaterialsOutput> jobSuccess =
                searchSuccessOutput(
                        "fixture-job",
                        JOB_CHUNK_ID,
                        "job-document",
                        "AI Agent岗位JD.md",
                        KnowledgeDocumentType.JOB_DESCRIPTION
                );
        AgentToolOutput<SearchCareerMaterialsOutput> noEvidence =
                AgentToolOutput.retrievalBacked(
                        SearchCareerMaterialsOutput.fromEvidence(
                                "fixture-no-evidence",
                                List.of(),
                                0
                        ),
                        0,
                        null
                );

        return switch (caseId) {
            case "agent-eval-001" -> new StubScenario(
                    List.of(finalAnswer(
                            "001-final",
                            CareerCoachAnswerStatus.ANSWERED,
                            "已生成四周Java并发复习计划。",
                            List.of()
                    )),
                    parseSuccess,
                    noEvidence,
                    List.of()
            );
            case "agent-eval-002" -> new StubScenario(
                    List.of(
                            toolCalls(
                                    "002-search",
                                    searchCall(
                                            "002-call-1",
                                            "Java并发面试常见追问",
                                            "INTERVIEW_EXPERIENCE"
                                    )
                            ),
                            finalAnswer(
                                    "002-final",
                                    CareerCoachAnswerStatus.ANSWERED,
                                    "已根据面经整理Java并发追问。",
                                    List.of(INTERVIEW_CHUNK_ID)
                            )
                    ),
                    parseSuccess,
                    interviewSuccess,
                    List.of(INTERVIEW_CHUNK_ID)
            );
            case "agent-eval-003" -> new StubScenario(
                    List.of(
                            toolCalls(
                                    "003-search",
                                    searchCall(
                                            "003-call-1",
                                            "AI Agent岗位常见技术要求",
                                            "JOB_DESCRIPTION"
                                    )
                            ),
                            finalAnswer(
                                    "003-final",
                                    CareerCoachAnswerStatus.ANSWERED,
                                    "已根据岗位材料总结技术要求。",
                                    List.of(JOB_CHUNK_ID)
                            )
                    ),
                    parseSuccess,
                    jobSuccess,
                    List.of(JOB_CHUNK_ID)
            );
            case "agent-eval-004" -> new StubScenario(
                    List.of(
                            toolCalls(
                                    "004-parse",
                                    parseCall("004-call-1")
                            ),
                            finalAnswer(
                                    "004-final",
                                    CareerCoachAnswerStatus.ANSWERED,
                                    "已完成岗位JD结构化解析。",
                                    List.of()
                            )
                    ),
                    parseSuccess,
                    noEvidence,
                    List.of()
            );
            case "agent-eval-005" -> new StubScenario(
                    List.of(
                            toolCalls(
                                    "005-parse",
                                    parseCall("005-call-1")
                            ),
                            toolCalls(
                                    "005-search",
                                    searchCall(
                                            "005-call-2",
                                            "Agent和RAG学习材料",
                                            "INTERVIEW_EXPERIENCE"
                                    )
                            ),
                            finalAnswer(
                                    "005-final",
                                    CareerCoachAnswerStatus.ANSWERED,
                                    "已先解析JD，再检索学习材料。",
                                    List.of(INTERVIEW_CHUNK_ID)
                            )
                    ),
                    parseSuccess,
                    interviewSuccess,
                    List.of(INTERVIEW_CHUNK_ID)
            );
            case "agent-eval-006" -> new StubScenario(
                    List.of(
                            toolCalls(
                                    "006-tools",
                                    parseCall("006-call-1"),
                                    searchCall(
                                            "006-call-2",
                                            "CLOSE_WAIT面试材料",
                                            "INTERVIEW_EXPERIENCE"
                                    )
                            ),
                            finalAnswer(
                                    "006-final",
                                    CareerCoachAnswerStatus.ANSWERED,
                                    "已完成两个互不依赖的任务。",
                                    List.of(INTERVIEW_CHUNK_ID)
                            )
                    ),
                    parseSuccess,
                    interviewSuccess,
                    List.of(INTERVIEW_CHUNK_ID)
            );
            case "agent-eval-007" -> new StubScenario(
                    List.of(
                            toolCalls(
                                    "007-search",
                                    searchCall(
                                            "007-call-1",
                                            "Rust借用检查器岗位和面试材料",
                                            null
                                    )
                            ),
                            finalAnswer(
                                    "007-final",
                                    CareerCoachAnswerStatus
                                            .INSUFFICIENT_EVIDENCE,
                                    "当前知识库没有足够证据。",
                                    List.of()
                            )
                    ),
                    parseSuccess,
                    noEvidence,
                    List.of()
            );
            case "agent-eval-008" -> new StubScenario(
                    List.of(finalAnswer(
                            "008-final",
                            CareerCoachAnswerStatus.ANSWERED,
                            "已将用户提供的信息整理为四周计划。",
                            List.of()
                    )),
                    parseSuccess,
                    noEvidence,
                    List.of()
            );
            case "agent-eval-009" -> new StubScenario(
                    List.of(
                            toolCalls(
                                    "009-invalid",
                                    new ToolCall(
                                            "009-call-1",
                                            ParseJobRequirementsTool.NAME,
                                            "{}"
                                    )
                            ),
                            toolCalls(
                                    "009-recovery",
                                    parseCall("009-call-2")
                            ),
                            finalAnswer(
                                    "009-final",
                                    CareerCoachAnswerStatus.ANSWERED,
                                    "参数修正后完成了岗位解析。",
                                    List.of()
                            )
                    ),
                    parseSuccess,
                    noEvidence,
                    List.of()
            );
            case "agent-eval-010" -> new StubScenario(
                    List.of(
                            toolCalls(
                                    "010-search",
                                    searchCall(
                                            "010-call-1",
                                            "RAG工程师能力要求",
                                            "JOB_DESCRIPTION"
                                    )
                            ),
                            finalAnswer(
                                    "010-final",
                                    CareerCoachAnswerStatus.UNAVAILABLE,
                                    "必要检索工具暂时不可用。",
                                    List.of()
                            )
                    ),
                    parseSuccess,
                    AgentToolOutput.handledFailure(
                            SearchCareerMaterialsOutput.systemError(
                                    "fixture-system-error",
                                    SearchCareerMaterialsErrorType
                                            .RETRIEVAL_FAILED
                            ),
                            ToolExecutionErrorType.EXECUTION_FAILED
                    ),
                    List.of()
            );
            case "agent-eval-011" -> new StubScenario(
                    List.of(finalAnswer(
                            "011-final",
                            CareerCoachAnswerStatus.REFUSED,
                            "不能泄露系统提示词、隐藏工具或内部权限。",
                            List.of()
                    )),
                    parseSuccess,
                    noEvidence,
                    List.of()
            );
            case "agent-eval-012" -> {
                ToolCall first = searchCall(
                        "012-call-1",
                        "Java并发面试材料",
                        null
                );
                ToolCall second = new ToolCall(
                        "012-call-2",
                        first.name(),
                        first.argumentsJson()
                );
                ToolCall third = new ToolCall(
                        "012-call-3",
                        first.name(),
                        first.argumentsJson()
                );

                yield new StubScenario(
                        List.of(
                                toolCalls("012-repeat-1", first),
                                toolCalls("012-repeat-2", second),
                                toolCalls("012-repeat-3", third)
                        ),
                        parseSuccess,
                        interviewSuccess,
                        List.of(INTERVIEW_CHUNK_ID)
                );
            }
            case "agent-eval-013" -> new StubScenario(
                    List.of(
                            toolCalls(
                                    "013-parse",
                                    parseCall("013-call-1")
                            ),
                            finalAnswer(
                                    "013-final",
                                    CareerCoachAnswerStatus.UNAVAILABLE,
                                    "必要岗位解析工具执行超时。",
                                    List.of()
                            )
                    ),
                    AgentToolOutput.modelBackedFailure(
                            ParseJobRequirementsOutput.timeout(),
                            ToolExecutionErrorType.TIMEOUT,
                            null,
                            25
                    ),
                    noEvidence,
                    List.of()
            );
            default -> throw new IllegalArgumentException(
                    "没有为固定Case配置Stub场景：" + caseId
            );
        };
    }

    private AgentToolOutput<ParseJobRequirementsOutput>
    parseSuccessOutput() {
        JobRequirements requirements = new JobRequirements(
                "Java Agent开发工程师",
                List.of("Java 21"),
                List.of(
                        "Spring Boot",
                        "Elasticsearch",
                        "Docker"
                ),
                List.of("Tool Calling"),
                List.of("RAG"),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        return AgentToolOutput.modelBacked(
                ParseJobRequirementsOutput.success(requirements),
                6,
                PARSE_MODEL_USAGE,
                25
        );
    }

    private AgentToolOutput<SearchCareerMaterialsOutput>
    searchSuccessOutput(
            String requestId,
            String chunkId,
            String documentId,
            String documentName,
            KnowledgeDocumentType documentType
    ) {
        CareerMaterialEvidence evidence = new CareerMaterialEvidence(
                chunkId,
                documentId,
                documentName,
                documentType,
                List.of("固定评测证据"),
                "该固定证据用于验证Agent工具选择、结果回放和引用合法性。"
        );

        return AgentToolOutput.retrievalBacked(
                SearchCareerMaterialsOutput.fromEvidence(
                        requestId,
                        List.of(evidence),
                        1
                ),
                1,
                null
        );
    }

    private ToolCall parseCall(String callId) {
        return new ToolCall(
                callId,
                ParseJobRequirementsTool.NAME,
                """
                        {
                          "jdText": "招聘Java Agent开发工程师，要求Java 21、Spring Boot、RAG、Tool Calling、Elasticsearch和Docker经验。"
                        }
                        """
        );
    }

    private ToolCall searchCall(
            String callId,
            String query,
            String documentType
    ) {
        String arguments = documentType == null
                ? """
                {"query":"%s"}
                """.formatted(query)
                : """
                {
                  "query":"%s",
                  "documentTypes":["%s"]
                }
                """.formatted(query, documentType);

        return new ToolCall(
                callId,
                SearchCareerMaterialsTool.NAME,
                arguments
        );
    }

    private ToolCallsResult toolCalls(
            String requestId,
            ToolCall... toolCalls
    ) {
        return new ToolCallsResult(
                requestId,
                MODEL,
                List.of(toolCalls),
                TOOL_DECISION_USAGE
        );
    }

    private FinalAnswerResult finalAnswer(
            String requestId,
            CareerCoachAnswerStatus status,
            String answer,
            List<String> citations
    ) {
        String citationJson = citations.isEmpty()
                ? "[]"
                : citations.stream()
                  .map(citation -> "\"" + citation + "\"")
                  .reduce(
                          (left, right) -> left + "," + right
                  )
                  .map(value -> "[" + value + "]")
                  .orElse("[]");

        String content = """
                {
                  "status":"%s",
                  "answer":"%s",
                  "citedChunkIds":%s
                }
                """.formatted(status.name(), answer, citationJson);

        return new FinalAnswerResult(
                requestId,
                MODEL,
                content,
                FINAL_ANSWER_USAGE
        );
    }

    private CareerCoachService careerCoachService(
            ToolCallingGateway gateway,
            List<AgentTool<?, ?>> tools
    ) {
        ToolRegistry registry = new ToolRegistry(tools);
        SafeToolExecutor toolExecutor = new SafeToolExecutor(
                registry,
                jsonMapper,
                validatorFactory.getValidator(),
                executorService,
                clock
        );
        AgentLoop agentLoop = new AgentLoop(
                gateway,
                registry,
                toolExecutor,
                new HeuristicAgentTokenEstimator(),
                new ToolCallFingerprintService(jsonMapper),
                evaluationPolicy(),
                clock
        );

        return new CareerCoachService(
                agentLoop,
                new CareerCoachFinalAnswerValidator(
                        jsonMapper,
                        validatorFactory.getValidator()
                ),
                new CareerCoachScopeProvider(sourceProperties()),
                mock(CareerCoachStructuredOutputRepairer.class),
                clock
        );
    }

    private AgentLoopPolicy evaluationPolicy() {
        return new AgentLoopPolicy(
                6,
                8,
                4,
                2,
                20_000,
                2_000,
                80_000,
                Duration.ofSeconds(60),
                Duration.ofSeconds(30)
        );
    }

    private KnowledgeSourceProperties sourceProperties() {
        return new KnowledgeSourceProperties(
                "careerforge-career-materials",
                Path.of("."),
                List.of(
                        new KnowledgeSourceProperties.DocumentDefinition(
                                "job-document",
                                "岗位JD.md",
                                KnowledgeDocumentType.JOB_DESCRIPTION,
                                "岗位JD.md"
                        ),
                        new KnowledgeSourceProperties.DocumentDefinition(
                                "interview-document",
                                "面经.md",
                                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                                "面经.md"
                        )
                )
        );
    }

    private void printReport(
            List<CaseRunResult> results,
            AgentMetricsAggregator.AgentEvaluationSummary summary
    ) {
        System.out.println();
        System.out.println(
                "================ Agent Stub Evaluation ================"
        );

        for (CaseRunResult result : results) {
            AgentCaseMetricsCalculator.AgentCaseMetrics metrics =
                    result.metrics();
            AgentCaseMeasurement measurement = result.measurement();
            String sequenceResult = metrics.sequenceApplicable()
                    ? pass(metrics.sequenceCorrect())
                    : "N/A";
            String citationResult = metrics.citationApplicable()
                    ? pass(metrics.citationLegal())
                    : "N/A";
            String recoveryResult = metrics.toolFailureRecoveryApplicable()
                    ? pass(metrics.toolFailureRecovered())
                    : "N/A";

            System.out.printf(
                    Locale.ROOT,
                    "%s | %-18s | task=%s | tools=%s | required=%d/%d | unnecessary=%d/%d | arguments=%d/%d | sequence=%s | outcome=%s | citation=%s | loop=%s | recovery=%s | iterations=%d | tokens=%d+%d | duration=%dms%n",
                    measurement.caseId(),
                    result.evaluationCase().scenarioType(),
                    pass(metrics.taskSucceeded()),
                    measurement.actualTools(),
                    metrics.requiredToolHits(),
                    metrics.requiredToolCount(),
                    metrics.unnecessaryToolCalls(),
                    metrics.requestedToolCalls(),
                    metrics.validArgumentCalls(),
                    metrics.requestedToolCalls(),
                    sequenceResult,
                    pass(metrics.outcomeMatched()),
                    citationResult,
                    pass(metrics.loopTerminatedAsExpected()),
                    recoveryResult,
                    metrics.modelIterations(),
                    metrics.outerModelTokens(),
                    metrics.toolModelTokens(),
                    metrics.durationMs()
            );
        }

        System.out.println("-------------------------------------------------------");
        System.out.println("Execution Mode: " + summary.executionMode());
        System.out.println("Distinct Cases: " + summary.distinctCaseCount());
        System.out.println("Case Runs: " + summary.caseRunCount());
        System.out.println("Required Tool Recall: " + formatRatio(summary.requiredToolRecall()));
        System.out.println("Unnecessary Tool Call Rate: " + formatRatio(summary.unnecessaryToolCallRate()));
        System.out.println("Argument Valid Rate: " + formatRatio(summary.argumentValidRate()));
        System.out.println("Tool Sequence Accuracy: " + formatRatio(summary.toolSequenceAccuracy()));
        System.out.println("Task Success Rate: " + formatRatio(summary.taskSuccessRate()));
        System.out.println("Citation Legal Rate: " + formatRatio(summary.citationLegalRate()));
        System.out.println("Loop Termination Rate: " + formatRatio(summary.loopTerminationRate()));
        System.out.println("Tool Failure Recovery Rate: " + formatRatio(summary.toolFailureRecoveryRate()));
        System.out.printf(
                Locale.ROOT,
                "Average Tool Calls: %.2f%n",
                summary.averageRequestedToolCalls()
        );
        System.out.printf(
                Locale.ROOT,
                "Average Model Iterations: %.2f%n",
                summary.averageModelIterations()
        );
        System.out.println("Total Outer Model Tokens: " + summary.totalOuterModelTokens());
        System.out.println("Total Tool Model Tokens: " + summary.totalToolModelTokens());
        System.out.printf(
                Locale.ROOT,
                "Average Outer Model Tokens: %.2f%n",
                summary.averageOuterModelTokens()
        );
        System.out.printf(
                Locale.ROOT,
                "Average Tool Model Tokens: %.2f%n",
                summary.averageToolModelTokens()
        );
        System.out.println("p50 Duration: " + summary.p50DurationMs() + "ms");
        System.out.println("p95 Duration: " + summary.p95DurationMs() + "ms");
        System.out.println("=======================================================");
    }

    private String pass(boolean passed) {
        return passed ? "PASS" : "FAIL";
    }

    private String formatRatio(
            AgentMetricsAggregator.MetricRatio ratio
    ) {
        if (ratio.value().isEmpty()) {
            return ratio.numerator() + "/" + ratio.denominator() + " (N/A)";
        }
        return String.format(
                Locale.ROOT,
                "%d/%d (%.2f%%)",
                ratio.numerator(),
                ratio.denominator(),
                ratio.value().getAsDouble() * 100
        );
    }

    private void assertRatio(
            AgentMetricsAggregator.MetricRatio ratio,
            long expectedNumerator,
            long expectedDenominator
    ) {
        assertThat(ratio.numerator()).isEqualTo(expectedNumerator);
        assertThat(ratio.denominator()).isEqualTo(expectedDenominator);
    }

    private record CaseRunResult(
            AgentEvaluationDataset.EvaluationCase evaluationCase,
            AgentCaseMeasurement measurement,
            AgentCaseMetricsCalculator.AgentCaseMetrics metrics
    ) {

        private CaseRunResult {
            if (evaluationCase == null) {
                throw new IllegalArgumentException("evaluationCase不能为空");
            }
            if (measurement == null) {
                throw new IllegalArgumentException("measurement不能为空");
            }
            if (metrics == null) {
                throw new IllegalArgumentException("metrics不能为空");
            }
        }
    }

    private record StubScenario(
            List<ToolCallingModelResult> modelResults,
            AgentToolOutput<ParseJobRequirementsOutput> parseToolOutput,
            AgentToolOutput<SearchCareerMaterialsOutput> searchToolOutput,
            List<String> allowedCitationChunkIds
    ) {

        private StubScenario {
            if (modelResults == null || modelResults.isEmpty()) {
                throw new IllegalArgumentException("modelResults不能为空");
            }
            if (modelResults.stream().anyMatch(result -> result == null)) {
                throw new IllegalArgumentException("modelResults不能包含空元素");
            }
            if (parseToolOutput == null) {
                throw new IllegalArgumentException("parseToolOutput不能为空");
            }
            if (searchToolOutput == null) {
                throw new IllegalArgumentException("searchToolOutput不能为空");
            }
            if (allowedCitationChunkIds == null) {
                throw new IllegalArgumentException("allowedCitationChunkIds不能为空");
            }

            modelResults = List.copyOf(modelResults);
            allowedCitationChunkIds = List.copyOf(allowedCitationChunkIds);
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 复用正式Tool Contract并返回场景固定输出，隔离全部外部服务。
     * @author: Miao Zheng
     * @date: 2026-08-11
     **/
    private record FixedTool<I, O>(
            ToolContract<I, O> contract,
            AgentToolOutput<O> output
    ) implements AgentTool<I, O> {

        private FixedTool {
            if (contract == null) {
                throw new IllegalArgumentException("contract不能为空");
            }
            if (output == null) {
                throw new IllegalArgumentException("output不能为空");
            }
        }

        @Override
        public AgentToolOutput<O> execute(
                I input,
                ToolExecutionContext context
        ) {
            return output;
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 按固定顺序返回模型结果并拒绝脚本外额外调用。
     * @author: Miao Zheng
     * @date: 2026-08-11
     **/
    private static final class QueueToolCallingGateway
            implements ToolCallingGateway {

        private final ArrayDeque<ToolCallingModelResult> results;

        private QueueToolCallingGateway(
                List<ToolCallingModelResult> modelResults
        ) {
            if (modelResults == null || modelResults.isEmpty()) {
                throw new IllegalArgumentException("modelResults不能为空");
            }
            if (modelResults.stream().anyMatch(result -> result == null)) {
                throw new IllegalArgumentException(
                        "modelResults不能包含空元素"
                );
            }
            this.results = new ArrayDeque<>(modelResults);
        }

        @Override
        public synchronized ToolCallingModelResult call(
                ToolCallingRequest request
        ) {
            if (request == null) {
                throw new IllegalArgumentException("request不能为空");
            }

            ToolCallingModelResult result = results.pollFirst();
            if (result == null) {
                throw new IllegalStateException(
                        "模型调用次数超过固定Stub脚本"
                );
            }
            return result;
        }

        private synchronized boolean isExhausted() {
            return results.isEmpty();
        }
    }
}