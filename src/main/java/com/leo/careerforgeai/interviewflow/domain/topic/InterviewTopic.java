package com.leo.careerforgeai.interviewflow.domain.topic;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示快速面试计划覆盖的一个考查目标
 * @author: Miao Zheng
 * @date: 2026-09-30
 * @param topicId 考查目标ID
 * @param sequenceNo 面试内顺序，从1开始
 * @param objective 内部考查目标说明，不直接向用户展示
 * @param status 当前生命周期状态
 */
public record InterviewTopic(
        UUID topicId,
        int sequenceNo,
        String objective,
        TopicStatus status
) {

    public InterviewTopic {
        Objects.requireNonNull(topicId, "topicId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        if (sequenceNo <= 0) {
            throw new IllegalArgumentException("sequenceNo必须大于0");
        }
        objective = requireText(objective, "objective");
    }

    public static InterviewTopic planned(
            UUID topicId,
            int sequenceNo,
            String objective
    ) {
        return new InterviewTopic(
                topicId,
                sequenceNo,
                objective,
                TopicStatus.PLANNED
        );
    }

    public InterviewTopic activate() {
        requireStatus(TopicStatus.PLANNED, "只有已规划的考查目标可以激活");
        return withStatus(TopicStatus.ACTIVE);
    }

    public InterviewTopic complete() {
        requireStatus(TopicStatus.ACTIVE, "只有进行中的考查目标可以完成");
        return withStatus(TopicStatus.COMPLETED);
    }

    public InterviewTopic skip() {
        if (status != TopicStatus.PLANNED && status != TopicStatus.ACTIVE) {
            throw new IllegalStateException("只有已规划或进行中的考查目标可以跳过");
        }
        return withStatus(TopicStatus.SKIPPED);
    }

    public InterviewTopic invalidate() {
        requireStatus(TopicStatus.PLANNED, "只有未开始的考查目标可以作废");
        return withStatus(TopicStatus.INVALIDATED);
    }

    private InterviewTopic withStatus(TopicStatus nextStatus) {
        return new InterviewTopic(topicId, sequenceNo, objective, nextStatus);
    }

    private void requireStatus(TopicStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
