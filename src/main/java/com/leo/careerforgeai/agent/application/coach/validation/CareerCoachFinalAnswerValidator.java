package com.leo.careerforgeai.agent.application.coach.validation;

import com.leo.careerforgeai.agent.application.coach.dto.CareerCoachModelOutput;
import com.leo.careerforgeai.agent.application.tool.career.search.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.domain.tool.career.search.CareerMaterialEvidence;
import com.leo.careerforgeai.agent.domain.tool.career.search.SearchCareerMaterialsOutput;
import com.leo.careerforgeai.agent.domain.tool.career.search.SearchCareerMaterialsStatus;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import com.leo.careerforgeai.model.exception.structured.StructuredOutputException;
import com.leo.careerforgeai.model.exception.structured.StructuredOutputFailureReason;
import com.leo.careerforgeai.model.exception.structured.StructuredOutputFailureStage;
import com.leo.careerforgeai.model.infrastructure.json.JacksonStructuredOutputFailureClassifier;
import jakarta.validation.ConstraintViolation;
import tools.jackson.core.JsonParser;

import java.util.Comparator;

/**
 * @program: CareerForge-AI
 * @description: 将模型最终JSON转换为可信回答，并验证引用来自本轮成功证据工具结果。
 * @author: Miao Zheng
 * @date: 2026-08-07 03:30
 **/
@Component
public final class CareerCoachFinalAnswerValidator {

    private static final int MAX_FINAL_CONTENT_CHARS = 20_000;
    private static final Pattern CHUNK_ID_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final JsonMapper jsonMapper;
    private final Validator validator;

    public CareerCoachFinalAnswerValidator(JsonMapper jsonMapper, Validator validator) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    /** 校验同一次Agent Loop的模型最终输出和工具证据，并返回可信回答。 */
    public CareerCoachAnswer validate(AgentLoopResult loopResult) {
        if (loopResult == null || loopResult.status() != AgentRunStatus.COMPLETED) {
            throw failure(CareerCoachFinalAnswerErrorType.AGENT_RESULT_INVALID, "Agent未生成可校验的最终回答");
        }
        return validate(loopResult.finalContent(), loopResult.toolResults());
    }

    public CareerCoachAnswer validate(String finalContent, java.util.List<ToolExecutionResult> toolResults) {
        if (toolResults == null || toolResults.stream().anyMatch(java.util.Objects::isNull)) {
            throw failure(CareerCoachFinalAnswerErrorType.TOOL_RESULT_INVALID, "Agent工具结果集合不合法");
        }

        CareerCoachModelOutput modelOutput = parseModelOutput(finalContent);
        Set<String> allowedChunkIds = collectAllowedChunkIds(toolResults);
        for (String citedChunkId : modelOutput.citedChunkIds()) {
            if (!allowedChunkIds.contains(citedChunkId)) {
                StructuredOutputException structured = new StructuredOutputException(
                        StructuredOutputFailureStage.REFERENCE_VALIDATION,
                        StructuredOutputFailureReason.REFERENCE_NOT_ALLOWED,
                        "$.citedChunkIds",
                        "最终回答包含未经本轮证据工具授权的引用",
                        null
                );
                throw structuredFailure(CareerCoachFinalAnswerErrorType.CITATION_NOT_ALLOWED,
                        structured, finalContent);
            }
        }

        try {
            return new CareerCoachAnswer(modelOutput.status(), modelOutput.answer().strip(),
                    modelOutput.citedChunkIds());
        } catch (RuntimeException exception) {
            StructuredOutputException structured = new StructuredOutputException(
                    StructuredOutputFailureStage.BUSINESS_CONTRACT_VALIDATION,
                    StructuredOutputFailureReason.BUSINESS_INVARIANT_VIOLATION,
                    null,
                    "最终回答违反业务约束",
                    exception
            );
            throw structuredFailure(CareerCoachFinalAnswerErrorType.MODEL_OUTPUT_INVALID,
                    structured, finalContent);
        }
    }

    /** 严格解析并校验不可信的模型最终JSON。 */
    private CareerCoachModelOutput parseModelOutput(String finalContent) {
        try {
            return parseModelOutputStrict(finalContent);
        } catch (StructuredOutputException exception) {
            throw structuredFailure(CareerCoachFinalAnswerErrorType.MODEL_OUTPUT_INVALID,
                    exception, finalContent);
        }
    }

    private CareerCoachModelOutput parseModelOutputStrict(String finalContent) {
        if (finalContent == null || finalContent.isBlank() || finalContent.length() > MAX_FINAL_CONTENT_CHARS) {
            throw new StructuredOutputException(
                    StructuredOutputFailureStage.CONTENT_BOUNDARY_VALIDATION,
                    StructuredOutputFailureReason.EMPTY_OR_OVERSIZED_CONTENT,
                    null,
                    "模型最终输出为空或超过长度限制",
                    null
            );
        }

        String content = finalContent.strip();
        try (JsonParser parser = jsonMapper.createParser(content)) {
            if (parser.nextToken() == null) {
                throw new StructuredOutputException(
                        StructuredOutputFailureStage.CONTENT_BOUNDARY_VALIDATION,
                        StructuredOutputFailureReason.EMPTY_OR_OVERSIZED_CONTENT,
                        null,
                        "模型最终输出为空",
                        null
                );
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new StructuredOutputException(
                        StructuredOutputFailureStage.JSON_PARSING,
                        StructuredOutputFailureReason.TRAILING_TOKEN,
                        "$",
                        "模型最终输出包含尾随JSON内容",
                        null
                );
            }
        } catch (StructuredOutputException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw JacksonStructuredOutputFailureClassifier.parsing(exception,
                    "模型最终输出不是合法JSON");
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(content);
        } catch (JacksonException exception) {
            throw JacksonStructuredOutputFailureClassifier.parsing(exception,
                    "模型最终输出不是合法JSON");
        }
        if (root == null || !root.isObject()) {
            throw new StructuredOutputException(
                    StructuredOutputFailureStage.JSON_PARSING,
                    StructuredOutputFailureReason.ROOT_NOT_OBJECT,
                    "$",
                    "模型最终输出顶层必须是JSON对象",
                    null
            );
        }

        CareerCoachModelOutput output;
        try {
            output = jsonMapper.readerFor(CareerCoachModelOutput.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(root.toString());
        } catch (JacksonException exception) {
            throw JacksonStructuredOutputFailureClassifier.deserialization(exception,
                    "模型最终输出无法反序列化为目标结构");
        }

        Set<ConstraintViolation<CareerCoachModelOutput>> violations = validator.validate(output);
        if (!violations.isEmpty()) {
            String fieldPath = violations.stream()
                    .map(violation -> "$." + violation.getPropertyPath())
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            throw new StructuredOutputException(
                    StructuredOutputFailureStage.OUTPUT_STRUCTURE_VALIDATION,
                    StructuredOutputFailureReason.OUTPUT_CONSTRAINT_VIOLATION,
                    fieldPath,
                    "模型最终输出未通过字段约束",
                    null
            );
        }
        return output;
    }

    private CareerCoachFinalAnswerException structuredFailure(
            CareerCoachFinalAnswerErrorType errorType,
            StructuredOutputException exception,
            String rawOutput
    ) {
        return new CareerCoachFinalAnswerException(errorType, exception.getMessage(),
                exception, rawOutput);
    }

    /** 只从本轮成功的search_career_materials结果中收集合法Chunk ID。 */
    private Set<String> collectAllowedChunkIds(java.util.List<ToolExecutionResult> toolResults) {
        LinkedHashSet<String> allowedChunkIds = new LinkedHashSet<>();

        for (ToolExecutionResult toolResult : toolResults) {
            if (toolResult.status() != ToolExecutionStatus.SUCCESS
                    || !SearchCareerMaterialsTool.NAME.equals(toolResult.toolName())) {
                continue;
            }

            SearchCareerMaterialsOutput searchOutput = parseSearchOutput(toolResult);
            if (searchOutput.status() == SearchCareerMaterialsStatus.NO_EVIDENCE) continue;
            if (searchOutput.status() != SearchCareerMaterialsStatus.SUCCESS) {
                throw failure(CareerCoachFinalAnswerErrorType.TOOL_RESULT_INVALID, "成功工具结果包含非法业务状态");
            }

            for (CareerMaterialEvidence evidence : searchOutput.evidence()) {
                if (!CHUNK_ID_PATTERN.matcher(evidence.chunkId()).matches()) {
                    throw failure(CareerCoachFinalAnswerErrorType.TOOL_RESULT_INVALID, "证据工具返回了非法Chunk ID");
                }
                allowedChunkIds.add(evidence.chunkId());
            }
        }

        return Set.copyOf(allowedChunkIds);
    }

    /** 严格解析SafeToolExecutor生成的成功搜索工具结果。 */
    private SearchCareerMaterialsOutput parseSearchOutput(ToolExecutionResult toolResult) {
        try {
            JsonNode envelope = jsonMapper.readTree(toolResult.resultJson());
            if (envelope == null || !envelope.isObject()) {
                throw failure(CareerCoachFinalAnswerErrorType.TOOL_RESULT_INVALID, "证据工具结果不是合法对象");
            }

            JsonNode statusNode = envelope.get("status");
            JsonNode dataNode = envelope.get("data");
            JsonNode errorNode = envelope.get("error");

            if (statusNode == null || !statusNode.isTextual() || !"SUCCESS".equals(statusNode.asText())
                    || dataNode == null || !dataNode.isObject()
                    || errorNode == null || !errorNode.isNull()) {
                throw failure(CareerCoachFinalAnswerErrorType.TOOL_RESULT_INVALID, "证据工具成功结果信封不合法");
            }

            return jsonMapper.readerFor(SearchCareerMaterialsOutput.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(dataNode.toString());
        } catch (JacksonException exception) {
            throw failure(CareerCoachFinalAnswerErrorType.TOOL_RESULT_INVALID, "证据工具结果无法解析", exception);
        }
    }

    /** 创建具有稳定分类和安全消息的最终回答异常。 */
    private CareerCoachFinalAnswerException failure(CareerCoachFinalAnswerErrorType errorType, String safeMessage) {
        return new CareerCoachFinalAnswerException(errorType, safeMessage);
    }

    /** 创建保留内部原因但不泄露其消息的最终回答异常。 */
    private CareerCoachFinalAnswerException failure(CareerCoachFinalAnswerErrorType errorType, String safeMessage, Throwable cause) {
        return new CareerCoachFinalAnswerException(errorType, safeMessage, cause);
    }
}