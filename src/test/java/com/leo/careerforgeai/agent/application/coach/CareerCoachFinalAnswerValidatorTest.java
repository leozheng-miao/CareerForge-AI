package com.leo.careerforgeai.agent.application.coach;

import com.leo.careerforgeai.agent.application.tool.career.ParseJobRequirementsTool;
import com.leo.careerforgeai.agent.application.tool.career.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.loop.AgentToolCallTrace;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Career Coach最终模型输出、工具结果和Chunk引用之间的可信边界。
 * @author: Miao Zheng
 * @date: 2026-08-07 03:50
 **/
class CareerCoachFinalAnswerValidatorTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
    private static final String VALID_CHUNK_ID = "a".repeat(64);
    private static final String FABRICATED_CHUNK_ID = "b".repeat(64);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final CareerCoachFinalAnswerValidator validator =
            new CareerCoachFinalAnswerValidator(jsonMapper, validatorFactory.getValidator());

    /** 关闭测试使用的Validation资源。 */
    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("接受来自本轮成功搜索工具结果的合法引用")
    void shouldAcceptCitationFromSuccessfulSearchResult() {
        ToolExecutionResult searchResult = successfulSearchResult(VALID_CHUNK_ID);
        AgentLoopResult loopResult = completed(answeredJson("  根据证据，Atomic适合单变量原子更新。  ", VALID_CHUNK_ID), List.of(searchResult));

        CareerCoachAnswer answer = validator.validate(loopResult);

        assertThat(answer.status()).isEqualTo(CareerCoachAnswerStatus.ANSWERED);
        assertThat(answer.answer()).isEqualTo("根据证据，Atomic适合单变量原子更新。");
        assertThat(answer.citedChunkIds()).containsExactly(VALID_CHUNK_ID);
    }

    @Test
    @DisplayName("没有工具调用时允许返回不带引用的一般回答")
    void shouldAcceptAnswerWithoutCitation() {
        AgentLoopResult loopResult = completed(answeredJson("这是一般职业建议。"), List.of());

        CareerCoachAnswer answer = validator.validate(loopResult);

        assertThat(answer.status()).isEqualTo(CareerCoachAnswerStatus.ANSWERED);
        assertThat(answer.citedChunkIds()).isEmpty();
    }

    @Test
    @DisplayName("拒绝模型自行编造的Chunk引用")
    void shouldRejectFabricatedCitation() {
        AgentLoopResult loopResult = completed(answeredJson("伪造引用。", FABRICATED_CHUNK_ID),
                List.of(successfulSearchResult(VALID_CHUNK_ID)));

        assertError(loopResult, CareerCoachFinalAnswerErrorType.CITATION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("失败搜索工具和非搜索工具都不能授予引用资格")
    void shouldIgnoreFailedSearchAndNonSearchToolResults() {
        ToolExecutionResult failedSearch = ToolExecutionResult.failure(
                "call-1",
                SearchCareerMaterialsTool.NAME,
                """
                {"status":"FAILURE","data":null,"error":{"type":"EXECUTION_FAILED","message":"工具执行失败"}}
                """,
                ToolExecutionErrorType.EXECUTION_FAILED
        );
        ToolExecutionResult parseResult = ToolExecutionResult.success(
                "call-2",
                ParseJobRequirementsTool.NAME,
                "{}",
                1,
                null,
                null
        );
        AgentLoopResult loopResult = completed(answeredJson("非法引用。", VALID_CHUNK_ID),
                List.of(failedSearch, parseResult));

        assertError(loopResult, CareerCoachFinalAnswerErrorType.CITATION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("拒绝畸形的成功搜索工具结果信封")
    void shouldRejectMalformedSuccessfulSearchResult() {
        ToolExecutionResult malformedResult = ToolExecutionResult.success(
                "call-1",
                SearchCareerMaterialsTool.NAME,
                "{}",
                1,
                null,
                null
        );
        AgentLoopResult loopResult = completed(answeredJson("无引用回答。"), List.of(malformedResult));

        assertError(loopResult, CareerCoachFinalAnswerErrorType.TOOL_RESULT_INVALID);
    }

    @Test
    @DisplayName("拒绝搜索工具返回的非法Chunk ID")
    void shouldRejectInvalidChunkIdFromSearchResult() {
        ToolExecutionResult invalidResult = successfulSearchResult("invalid-chunk-id");
        AgentLoopResult loopResult = completed(answeredJson("无引用回答。"), List.of(invalidResult));

        assertError(loopResult, CareerCoachFinalAnswerErrorType.TOOL_RESULT_INVALID);
    }

    @Test
    @DisplayName("拒绝非法JSON和包含额外字段的模型最终输出")
    void shouldRejectInvalidModelOutput() {
        assertError(completed("not-json", List.of()), CareerCoachFinalAnswerErrorType.MODEL_OUTPUT_INVALID);

        String outputWithExtraField = """
                {
                  "status":"ANSWERED",
                  "answer":"回答",
                  "citedChunkIds":[],
                  "unexpected":true
                }
                """;
        assertError(completed(outputWithExtraField, List.of()), CareerCoachFinalAnswerErrorType.MODEL_OUTPUT_INVALID);
    }

    @Test
    @DisplayName("拒绝重复引用和拒答状态携带引用")
    void shouldRejectInvalidCitationSemantics() {
        ToolExecutionResult searchResult = successfulSearchResult(VALID_CHUNK_ID);

        String duplicatedCitations = """
                {
                  "status":"ANSWERED",
                  "answer":"重复引用",
                  "citedChunkIds":["%s","%s"]
                }
                """.formatted(VALID_CHUNK_ID, VALID_CHUNK_ID);
        assertError(completed(duplicatedCitations, List.of(searchResult)),
                CareerCoachFinalAnswerErrorType.MODEL_OUTPUT_INVALID);

        String refusedWithCitation = """
                {
                  "status":"REFUSED",
                  "answer":"无法处理该请求。",
                  "citedChunkIds":["%s"]
                }
                """.formatted(VALID_CHUNK_ID);
        assertError(completed(refusedWithCitation, List.of(searchResult)),
                CareerCoachFinalAnswerErrorType.MODEL_OUTPUT_INVALID);
    }

    @Test
    @DisplayName("拒绝没有正常完成的Agent Loop结果")
    void shouldRejectNonCompletedAgentResult() {
        AgentRunTrace trace = new AgentRunTrace(
                "run-1",
                NOW,
                NOW,
                AgentRunStatus.FAILED,
                AgentTerminationReason.MODEL_FAILURE,
                List.of(),
                List.of()
        );
        AgentLoopResult loopResult = AgentLoopResult.terminated(
                AgentRunStatus.FAILED,
                AgentTerminationReason.MODEL_FAILURE,
                trace,
                List.of()
        );

        assertError(loopResult, CareerCoachFinalAnswerErrorType.AGENT_RESULT_INVALID);
    }

    @Test
    @DisplayName("接受不带引用的依赖不可用状态")
    void shouldAcceptUnavailableWithoutCitation() {
        String unavailableOutput = """
            {
              "status":"UNAVAILABLE",
              "answer":"职业材料工具暂时不可用，请稍后重试。",
              "citedChunkIds":[]
            }
            """;

        CareerCoachAnswer answer = validator.validate(
                completed(unavailableOutput, List.of())
        );

        assertThat(answer.status()).isEqualTo(CareerCoachAnswerStatus.UNAVAILABLE);
        assertThat(answer.citedChunkIds()).isEmpty();
    }

    /** 创建带一个可选Chunk引用的ANSWERED模型JSON。 */
    private String answeredJson(String answer, String... citedChunkIds) {
        String citations = citedChunkIds.length == 0
                ? ""
                : "\"" + String.join("\",\"", citedChunkIds) + "\"";
        return """
                {
                  "status":"ANSWERED",
                  "answer":"%s",
                  "citedChunkIds":[%s]
                }
                """.formatted(answer, citations);
    }

    /** 创建SafeToolExecutor格式的成功职业材料搜索结果。 */
    private ToolExecutionResult successfulSearchResult(String chunkId) {
        String resultJson = """
                {
                  "status":"SUCCESS",
                  "data":{
                    "status":"SUCCESS",
                    "requestId":"search-1",
                    "evidence":[{
                      "chunkId":"%s",
                      "documentId":"document-1",
                      "documentName":"面经.md",
                      "documentType":"INTERVIEW_EXPERIENCE",
                      "sectionPath":["Java并发"],
                      "content":"CAS"
                    }],
                    "usedContentChars":3,
                    "candidateCount":1,
                    "errorType":null
                  },
                  "error":null
                }
                """.formatted(chunkId);

        return ToolExecutionResult.success(
                "call-" + chunkId,
                SearchCareerMaterialsTool.NAME,
                resultJson,
                1,
                null,
                null
        );
    }

    /** 创建与工具结果顺序和关联信息一致的已完成Agent Loop结果。 */
    private AgentLoopResult completed(String finalContent, List<ToolExecutionResult> toolResults) {
        List<AgentToolCallTrace> toolTraces = new ArrayList<>();
        for (int index = 0; index < toolResults.size(); index++) {
            toolTraces.add(toolTrace(toolResults.get(index), index + 1));
        }

        AgentRunTrace trace = new AgentRunTrace(
                "run-1",
                NOW,
                NOW,
                AgentRunStatus.COMPLETED,
                AgentTerminationReason.FINAL_ANSWER,
                List.of(),
                toolTraces
        );
        return AgentLoopResult.completed(finalContent, trace, toolResults);
    }

    /** 根据工具结果创建与其元数据一致的Trace。 */
    private AgentToolCallTrace toolTrace(ToolExecutionResult result, int sequence) {
        ToolImplementationType implementationType =
                SearchCareerMaterialsTool.NAME.equals(result.toolName())
                        ? ToolImplementationType.RETRIEVAL_BACKED
                        : ToolImplementationType.MODEL_BACKED;

        return new AgentToolCallTrace(
                1,
                sequence,
                result.toolCallId(),
                result.toolName(),
                implementationType,
                result.status(),
                0,
                0,
                result.resultJson().length(),
                result.resultCount(),
                result.errorType(),
                result.modelUsage(),
                result.modelDurationMs()
        );
    }

    /** 断言最终回答验证失败并具有预期的安全错误分类。 */
    private void assertError(AgentLoopResult loopResult, CareerCoachFinalAnswerErrorType expectedType) {
        assertThatThrownBy(() -> validator.validate(loopResult))
                .isInstanceOf(CareerCoachFinalAnswerException.class)
                .satisfies(exception -> assertThat(
                        ((CareerCoachFinalAnswerException) exception).getErrorType()
                ).isEqualTo(expectedType));
    }
}