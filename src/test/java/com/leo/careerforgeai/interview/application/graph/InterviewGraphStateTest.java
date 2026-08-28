package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.InterviewWaitReason;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Interview Graph State版本、稳定字符串枚举和禁止框架对象边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
class InterviewGraphStateTest {

    @Test
    void shouldCreateVersionedStateUsingOnlyStableCheckpointValues() {
        UUID interviewId = UUID.randomUUID();
        Map<String, Object> data = new HashMap<>(
                InterviewGraphState.initialData(
                        interviewId,
                        InterviewMode.TARGETED_MOCK,
                        "a".repeat(64)
                )
        );
        data.put(InterviewGraphState.REVIEW_PLAN, InterviewReviewPlan.TECHNICAL_ONLY.name());

        UUID questionId = UUID.randomUUID();
        data.putAll(InterviewGraphState.waitingForAnswerUpdate(1, questionId));

        InterviewGraphState state = new InterviewGraphState(data);

        assertThat(state.schemaVersion()).isEqualTo(1);
        assertThat(state.interviewId()).isEqualTo(interviewId);
        assertThat(state.mode()).isEqualTo(InterviewMode.TARGETED_MOCK);
        assertThat(state.currentRound()).isEqualTo(1);
        assertThat(state.currentQuestionId()).contains(questionId);
        assertThat(state.answerId()).isEmpty();
        assertThat(state.waitReason())
                .contains(InterviewWaitReason.WAITING_FOR_ANSWER);
        assertThat(state.lastErrorCode()).isEmpty();
    }

    @Test
    void shouldRejectUnsupportedSchemaAndJacksonFrameworkObjects() {
        Map<String, Object> unsupportedVersion = new HashMap<>(
                InterviewGraphState.initialData(
                        UUID.randomUUID(),
                        InterviewMode.TARGETED_MOCK,
                        "a".repeat(64)
                )
        );
        unsupportedVersion.put(InterviewGraphState.SCHEMA_VERSION, 2);

        assertThatThrownBy(() -> new InterviewGraphState(unsupportedVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");

        Map<String, Object> unstableState = new HashMap<>(
                InterviewGraphState.initialData(
                        UUID.randomUUID(),
                        InterviewMode.TARGETED_MOCK,
                        "a".repeat(64)
                )
        );
        unstableState.put("jsonNode", JsonMapper.builder().build().createObjectNode());

        assertThatThrownBy(() -> new InterviewGraphState(unstableState))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不稳定");
    }
}