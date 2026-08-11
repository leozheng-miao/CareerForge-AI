package com.leo.careerforgeai.agent.evaluation.dataset;

import com.leo.careerforgeai.agent.application.coach.CareerCoachDefinition;
import com.leo.careerforgeai.agent.application.tool.career.parse.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.application.tool.career.search.ParseJobRequirementsTool;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 定义固定Agent评测集、预期工具行为、故障模式和成功标准。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public record AgentEvaluationDataset(
        String schemaVersion,
        String evaluationSetVersion,
        String contextVersion,
        int realRunsPerCase,
        List<EvaluationCase> cases
) {

    public static final String SUPPORTED_SCHEMA_VERSION = "agent-evaluation-v1";
    public static final String SUPPORTED_EVALUATION_SET_VERSION = "careerforge-agent-eval-v1";
    public static final int REQUIRED_REAL_RUNS_PER_CASE = 3;
    public static final int MINIMUM_CASE_COUNT = 13;
    public static final Set<String> SUPPORTED_TOOLS = Set.of(
            ParseJobRequirementsTool.NAME,
            SearchCareerMaterialsTool.NAME
    );

    public AgentEvaluationDataset {
        requireEqual("schemaVersion", SUPPORTED_SCHEMA_VERSION, schemaVersion);
        requireEqual("evaluationSetVersion", SUPPORTED_EVALUATION_SET_VERSION, evaluationSetVersion);
        requireEqual("contextVersion", CareerCoachDefinition.CONTEXT_VERSION, contextVersion);
        if (realRunsPerCase != REQUIRED_REAL_RUNS_PER_CASE) throw new IllegalArgumentException("真实正式评测每个Case必须运行3次");
        if (cases == null || cases.size() < MINIMUM_CASE_COUNT) throw new IllegalArgumentException("Agent评测集至少需要13条Case");
        if (cases.stream().anyMatch(evaluationCase -> evaluationCase == null)) throw new IllegalArgumentException("cases不能包含空元素");
        cases = List.copyOf(cases);

        Set<String> caseIds = new HashSet<>();
        Set<String> userMessages = new HashSet<>();
        EnumSet<ScenarioType> scenarioTypes = EnumSet.noneOf(ScenarioType.class);
        for (EvaluationCase evaluationCase : cases) {
            if (!caseIds.add(evaluationCase.caseId())) throw new IllegalArgumentException("caseId重复：" + evaluationCase.caseId());
            if (!userMessages.add(evaluationCase.userMessage())) throw new IllegalArgumentException("userMessage重复：" + evaluationCase.userMessage());
            scenarioTypes.add(evaluationCase.scenarioType());
        }
        if (!scenarioTypes.containsAll(EnumSet.allOf(ScenarioType.class))) throw new IllegalArgumentException("评测集没有覆盖全部ScenarioType");
    }

    public record EvaluationCase(
            String caseId,
            ScenarioType scenarioType,
            String userMessage,
            List<String> expectedTools,
            List<String> forbiddenTools,
            SequenceMode sequenceMode,
            List<String> expectedOrder,
            int maxToolCalls,
            boolean answerable,
            boolean requiredCitation,
            FaultMode faultMode,
            AgentRunStatus expectedRunStatus,
            AgentTerminationReason expectedTerminationReason,
            CareerCoachAnswerStatus expectedAnswerStatus,
            String labelReason
    ) {

        public EvaluationCase {
            if (caseId == null || !caseId.matches("agent-eval-[0-9]{3}")) throw new IllegalArgumentException("caseId格式不合法");
            requireText(userMessage, "userMessage");
            if (userMessage.length() > CareerCoachDefinition.MAX_USER_MESSAGE_CHARS) throw new IllegalArgumentException("userMessage超过长度限制");
            if (scenarioType == null) throw new IllegalArgumentException("scenarioType不能为空");
            expectedTools = copyToolList(expectedTools, "expectedTools");
            forbiddenTools = copyToolList(forbiddenTools, "forbiddenTools");
            expectedOrder = copyToolList(expectedOrder, "expectedOrder");
            if (sequenceMode == null) throw new IllegalArgumentException("sequenceMode不能为空");
            if (faultMode == null) throw new IllegalArgumentException("faultMode不能为空");
            if (expectedRunStatus == null) throw new IllegalArgumentException("expectedRunStatus不能为空");
            if (expectedTerminationReason == null) throw new IllegalArgumentException("expectedTerminationReason不能为空");
            if (maxToolCalls < 0 || maxToolCalls < expectedTools.size()) throw new IllegalArgumentException("maxToolCalls不能小于必需工具数");

            Set<String> overlap = new HashSet<>(expectedTools);
            overlap.retainAll(forbiddenTools);
            if (!overlap.isEmpty()) throw new IllegalArgumentException("必需工具和禁止工具不能重叠");
            Set<String> classifiedTools = new HashSet<>(expectedTools);
            classifiedTools.addAll(forbiddenTools);
            if (!classifiedTools.equals(SUPPORTED_TOOLS)) throw new IllegalArgumentException("每个Case必须分类全部公开工具");

            validateSequence(sequenceMode, expectedTools, expectedOrder);
            validateFault(scenarioType, faultMode);
            validateOutcome(expectedRunStatus, expectedTerminationReason, expectedAnswerStatus, answerable);
            if (requiredCitation && (!answerable || !expectedTools.contains(SearchCareerMaterialsTool.NAME))) {
                throw new IllegalArgumentException("requiredCitation只适用于需要检索证据的可回答Case");
            }
            requireText(labelReason, "labelReason");
        }
    }

    public enum ScenarioType {
        DIRECT_ANSWER,
        KNOWLEDGE_SEARCH,
        JD_PARSE,
        SEQUENTIAL_TOOLS,
        PARALLEL_TOOLS,
        NO_EVIDENCE,
        FORBIDDEN_TOOL,
        INVALID_ARGUMENTS,
        TOOL_FAILURE,
        PROMPT_INJECTION,
        REPEATED_CALL
    }

    public enum SequenceMode {
        NONE,
        ANY_ORDER,
        EXACT_ORDER
    }

    public enum FaultMode {
        NONE,
        SEARCH_NO_EVIDENCE,
        INVALID_ARGUMENTS_ONCE,
        TOOL_SYSTEM_ERROR,
        TOOL_TIMEOUT,
        REPEATED_IDENTICAL_CALL
    }

    private static List<String> copyToolList(List<String> tools, String fieldName) {
        if (tools == null || tools.stream().anyMatch(tool -> tool == null || tool.isBlank())) {
            throw new IllegalArgumentException(fieldName + "不能为空且不能包含空工具名");
        }
        if (new HashSet<>(tools).size() != tools.size()) throw new IllegalArgumentException(fieldName + "不能包含重复工具");
        if (!SUPPORTED_TOOLS.containsAll(tools)) throw new IllegalArgumentException(fieldName + "包含未知工具");
        return List.copyOf(tools);
    }

    private static void validateSequence(SequenceMode mode, List<String> expectedTools, List<String> expectedOrder) {
        switch (mode) {
            case NONE -> {
                if (!expectedTools.isEmpty() || !expectedOrder.isEmpty()) throw new IllegalArgumentException("NONE不能声明必需工具或顺序");
            }
            case ANY_ORDER -> {
                if (expectedTools.size() < 2 || !expectedOrder.isEmpty()) throw new IllegalArgumentException("ANY_ORDER至少需要两个工具且不能声明expectedOrder");
            }
            case EXACT_ORDER -> {
                if (expectedTools.isEmpty() || !expectedOrder.equals(expectedTools)) throw new IllegalArgumentException("EXACT_ORDER必须按expectedTools声明完整顺序");
            }
        }
    }

    private static void validateFault(ScenarioType scenarioType, FaultMode faultMode) {
        boolean valid = switch (scenarioType) {
            case NO_EVIDENCE -> faultMode == FaultMode.SEARCH_NO_EVIDENCE;
            case INVALID_ARGUMENTS -> faultMode == FaultMode.INVALID_ARGUMENTS_ONCE;
            case TOOL_FAILURE -> faultMode == FaultMode.TOOL_SYSTEM_ERROR || faultMode == FaultMode.TOOL_TIMEOUT;
            case REPEATED_CALL -> faultMode == FaultMode.REPEATED_IDENTICAL_CALL;
            default -> faultMode == FaultMode.NONE;
        };
        if (!valid) throw new IllegalArgumentException("scenarioType与faultMode不匹配");
    }

    private static void validateOutcome(AgentRunStatus runStatus, AgentTerminationReason terminationReason,
                                        CareerCoachAnswerStatus answerStatus, boolean answerable) {
        if (runStatus == AgentRunStatus.COMPLETED) {
            if (terminationReason != AgentTerminationReason.FINAL_ANSWER || answerStatus == null) {
                throw new IllegalArgumentException("COMPLETED必须产生FINAL_ANSWER和业务回答状态");
            }
        } else if (answerStatus != null) {
            throw new IllegalArgumentException("非COMPLETED终态不能声明业务回答状态");
        }
        if (answerable != (answerStatus == CareerCoachAnswerStatus.ANSWERED)) {
            throw new IllegalArgumentException("answerable与expectedAnswerStatus不一致");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
    }

    private static void requireEqual(String fieldName, String expected, String actual) {
        if (!expected.equals(actual)) throw new IllegalArgumentException(fieldName + "不受支持，expected=" + expected + ", actual=" + actual);
    }
}