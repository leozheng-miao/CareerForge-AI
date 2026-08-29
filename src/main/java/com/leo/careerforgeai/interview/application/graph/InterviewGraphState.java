package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.InterviewWaitReason;
import org.bsc.langgraph4j.state.AgentState;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.Optional;

/**
 * @program: CareerForge-AI
 * @description: 保存模拟面试Graph的ID、流程进度和稳定字符串元数据
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public final class InterviewGraphState extends AgentState {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String INTERVIEW_ID = "interviewId";
    public static final String MODE = "mode";
    public static final String INPUT_SNAPSHOT_HASH = "inputSnapshotHash";
    public static final String CURRENT_ROUND = "currentRound";
    public static final String CURRENT_QUESTION_ID = "currentQuestionId";
    public static final String ANSWER_ID = "answerId";
    public static final String REVIEW_PLAN = "reviewPlan";
    public static final String TECHNICAL_REVIEW_ID = "technicalReviewId";
    public static final String EVIDENCE_REVIEW_ID = "evidenceReviewId";
    public static final String ROUTE_DECISION = "routeDecision";
    public static final String REPORT_ID = "reportId";
    public static final String WAIT_REASON = "waitReason";
    public static final String LAST_ERROR_CODE = "lastErrorCode";

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public InterviewGraphState(Map<String, Object> initData) {
        super(validate(initData));
    }

    public static Map<String, Object> initialData(
            UUID interviewId,
            InterviewMode mode,
            String inputSnapshotHash
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(mode, "mode不能为空");
        requireSha256(inputSnapshotHash, INPUT_SNAPSHOT_HASH);

        return Map.of(
                SCHEMA_VERSION, CURRENT_SCHEMA_VERSION,
                INTERVIEW_ID, interviewId.toString(),
                MODE, mode.name(),
                INPUT_SNAPSHOT_HASH, inputSnapshotHash,
                CURRENT_ROUND, 0
        );
    }

    public int schemaVersion() {
        return requiredInteger(data(), SCHEMA_VERSION);
    }

    public UUID interviewId() {
        return UUID.fromString(requiredString(data(), INTERVIEW_ID));
    }

    public InterviewMode mode() {
        return InterviewMode.valueOf(requiredString(data(), MODE));
    }

    public String inputSnapshotHash() {
        return requiredString(data(), INPUT_SNAPSHOT_HASH);
    }

    public int currentRound() {
        return requiredInteger(data(), CURRENT_ROUND);
    }

    public Optional<UUID> currentQuestionId() {
        return optionalString(CURRENT_QUESTION_ID).map(UUID::fromString);
    }

    public Optional<UUID> answerId() {
        return optionalString(ANSWER_ID).map(UUID::fromString);
    }

    public Optional<InterviewReviewPlan> reviewPlan() {
        return optionalString(REVIEW_PLAN).map(InterviewReviewPlan::valueOf);
    }

    public Optional<UUID> technicalReviewId() {
        return optionalString(TECHNICAL_REVIEW_ID).map(UUID::fromString);
    }

    public Optional<UUID> evidenceReviewId() {
        return optionalString(EVIDENCE_REVIEW_ID).map(UUID::fromString);
    }

    public Optional<InterviewRouteDecision> routeDecision() {
        return optionalString(ROUTE_DECISION).map(InterviewRouteDecision::valueOf);
    }

    public Optional<InterviewWaitReason> waitReason() {
        return optionalString(WAIT_REASON).map(InterviewWaitReason::valueOf);
    }

    public Optional<InterviewFailureCode> lastErrorCode() {
        return optionalString(LAST_ERROR_CODE).map(InterviewFailureCode::valueOf);
    }

    public static Map<String, Object> waitingForAnswerUpdate(
            int roundNo,
            UUID questionId
    ) {
        if (roundNo < 1) throw new IllegalArgumentException("roundNo必须从1开始");
        Objects.requireNonNull(questionId, "questionId不能为空");
        return Map.of(
                CURRENT_ROUND, roundNo,
                CURRENT_QUESTION_ID, questionId.toString(),
                WAIT_REASON, InterviewWaitReason.WAITING_FOR_ANSWER.name()
        );
    }

    public static Map<String, Object> waitingForNextAnswerUpdate(int roundNo, UUID questionId) {
        if (roundNo < 2) throw new IllegalArgumentException("后续问题roundNo必须从2开始");
        Objects.requireNonNull(questionId, "questionId不能为空");
        return Map.of(
                CURRENT_ROUND, roundNo,
                CURRENT_QUESTION_ID, questionId.toString(),
                WAIT_REASON, InterviewWaitReason.WAITING_FOR_ANSWER.name(),
                ROUTE_DECISION, AgentState.MARK_FOR_REMOVAL
        );
    }

    public static Map<String, Object> clearCompletedRoundForNextQuestionUpdate() {
        return Map.of(
                ANSWER_ID, AgentState.MARK_FOR_REMOVAL,
                REVIEW_PLAN, AgentState.MARK_FOR_REMOVAL,
                TECHNICAL_REVIEW_ID, AgentState.MARK_FOR_REMOVAL,
                EVIDENCE_REVIEW_ID, AgentState.MARK_FOR_REMOVAL
        );
    }

    public static Map<String, Object> answerResumeUpdate(UUID answerId) {
        Objects.requireNonNull(answerId, "answerId不能为空");
        return Map.of(ANSWER_ID, answerId.toString());
    }

    public static Map<String, Object> clearWaitReasonUpdate() {
        return Map.of(WAIT_REASON, AgentState.MARK_FOR_REMOVAL);
    }

    public static Map<String, Object> routeDecisionUpdate(InterviewRouteDecision decision) {
        Objects.requireNonNull(decision, "decision不能为空");
        return Map.of(ROUTE_DECISION, decision.name());
    }

    public static Map<String, Object> supervisionDecisionUpdate(
            InterviewRouteDecision routeDecision,
            InterviewFailureCode failureCode
    ) {
        Objects.requireNonNull(routeDecision, "routeDecision不能为空");
        boolean failureRoute = routeDecision == InterviewRouteDecision.FINALIZE_FAILURE;
        if (failureRoute != (failureCode != null)) {
            throw new IllegalArgumentException("failureCode与routeDecision不匹配");
        }
        if (failureCode == null) return Map.of(ROUTE_DECISION, routeDecision.name());
        return Map.of(
                ROUTE_DECISION, routeDecision.name(),
                LAST_ERROR_CODE, failureCode.name()
        );
    }

    public static Map<String, Object> clearCompletedRoundUpdate() {
        return Map.of(
                ANSWER_ID, AgentState.MARK_FOR_REMOVAL,
                REVIEW_PLAN, AgentState.MARK_FOR_REMOVAL,
                TECHNICAL_REVIEW_ID, AgentState.MARK_FOR_REMOVAL,
                EVIDENCE_REVIEW_ID, AgentState.MARK_FOR_REMOVAL,
                ROUTE_DECISION, AgentState.MARK_FOR_REMOVAL,
                LAST_ERROR_CODE, AgentState.MARK_FOR_REMOVAL
        );
    }

    private static Map<String, Object> validate(Map<String, Object> state) {
        Objects.requireNonNull(state, "state不能为空");
        state.forEach((key, value) -> {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Graph State字段名不能为空");
            requireStableValue(key, value);
        });

        int schemaVersion = requiredInteger(state, SCHEMA_VERSION);
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的InterviewGraphState schemaVersion: " + schemaVersion);
        }

        requireUuid(state, INTERVIEW_ID);
        requireEnum(state, MODE, InterviewMode.class);
        requireSha256(requiredString(state, INPUT_SNAPSHOT_HASH), INPUT_SNAPSHOT_HASH);

        if (requiredInteger(state, CURRENT_ROUND) < 0) {
            throw new IllegalArgumentException("currentRound不能小于0");
        }

        requireOptionalUuid(state, CURRENT_QUESTION_ID);
        requireOptionalUuid(state, ANSWER_ID);
        requireOptionalEnum(state, REVIEW_PLAN, InterviewReviewPlan.class);
        requireOptionalUuid(state, TECHNICAL_REVIEW_ID);
        requireOptionalUuid(state, EVIDENCE_REVIEW_ID);
        requireOptionalEnum(state, ROUTE_DECISION, InterviewRouteDecision.class);
        requireOptionalUuid(state, REPORT_ID);
        requireOptionalEnum(state, WAIT_REASON, InterviewWaitReason.class);
        requireOptionalEnum(state, LAST_ERROR_CODE, InterviewFailureCode.class);
        return state;
    }

    private static void requireStableValue(String path, Object value) {
        if (value == null) throw new IllegalArgumentException(path + "不能为空");
        if (isStableScalar(value)) return;

        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                requireStableValue(path + "[" + index + "]", list.get(index));
            }
            return;
        }

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                    throw new IllegalArgumentException(path + "包含非法Map字段名");
                }
                requireStableValue(path + "." + key, entry.getValue());
            }
            return;
        }

        throw new IllegalArgumentException(path + "包含不稳定的Graph State类型: " + value.getClass().getName());
    }

    private static boolean isStableScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    private Optional<String> optionalString(String key) {
        Object value = data().get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException(key + "必须是非空字符串");
        }
        return Optional.of(stringValue);
    }

    private static String requiredString(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException(key + "必须是非空字符串");
        }
        return stringValue;
    }

    private static int requiredInteger(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(key + "必须是整数");
        }

        long longValue = ((Number) value).longValue();
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + "超出整数范围");
        }
        return (int) longValue;
    }

    private static void requireUuid(Map<String, Object> state, String key) {
        try {
            UUID.fromString(requiredString(state, key));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + "必须是合法UUID", exception);
        }
    }

    private static void requireOptionalUuid(Map<String, Object> state, String key) {
        if (state.containsKey(key)) requireUuid(state, key);
    }

    private static <E extends Enum<E>> void requireEnum(Map<String, Object> state, String key, Class<E> enumType) {
        try {
            Enum.valueOf(enumType, requiredString(state, key));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + "不是合法的" + enumType.getSimpleName(), exception);
        }
    }

    private static <E extends Enum<E>> void requireOptionalEnum(
            Map<String, Object> state,
            String key,
            Class<E> enumType
    ) {
        if (state.containsKey(key)) requireEnum(state, key, enumType);
    }

    private static void requireSha256(String value, String fieldName) {
        if (value == null || !SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "必须是64位小写SHA-256");
        }
    }
}