package com.leo.careerforgeai.interviewflow.domain.turn;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示一条已完整生成并可持久化的主问题或追问
 * @author: Miao Zheng
 * @date: 2026-09-30
 * @param questionId 问题ID
 * @param topicId 所属考查目标ID
 * @param type 主问题或追问
 * @param displayOrder 面试内展示顺序，从1开始
 * @param content 完整题干
 * @param status 当前生命周期状态
 */
public record InterviewQuestion(
        UUID questionId,
        UUID topicId,
        QuestionType type,
        int displayOrder,
        String content,
        QuestionStatus status
) {

    public InterviewQuestion {
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(topicId, "topicId不能为空");
        Objects.requireNonNull(type, "type不能为空");
        Objects.requireNonNull(status, "status不能为空");
        if (displayOrder <= 0) {
            throw new IllegalArgumentException("displayOrder必须大于0");
        }
        content = requireText(content, "content");
    }

    public static InterviewQuestion reserved(
            UUID questionId,
            UUID topicId,
            QuestionType type,
            int displayOrder,
            String content
    ) {
        return new InterviewQuestion(
                questionId,
                topicId,
                type,
                displayOrder,
                content,
                QuestionStatus.RESERVED
        );
    }

    public InterviewQuestion present() {
        requireStatus(QuestionStatus.RESERVED, "只有已预留的问题可以展示");
        return withStatus(QuestionStatus.PRESENTED);
    }

    public InterviewQuestion close() {
        requireStatus(QuestionStatus.PRESENTED, "只有已展示的问题可以关闭");
        return withStatus(QuestionStatus.CLOSED);
    }

    public InterviewQuestion skip() {
        requireStatus(QuestionStatus.PRESENTED, "只有已展示的问题可以跳过");
        return withStatus(QuestionStatus.SKIPPED);
    }

    public InterviewQuestion invalidate() {
        if (status != QuestionStatus.RESERVED) {
            throw new IllegalStateException("只有未展示的预留问题可以作废");
        }
        return withStatus(QuestionStatus.INVALIDATED);
    }

    private InterviewQuestion withStatus(QuestionStatus nextStatus) {
        return new InterviewQuestion(
                questionId,
                topicId,
                type,
                displayOrder,
                content,
                nextStatus
        );
    }

    private void requireStatus(QuestionStatus expected, String message) {
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

    public enum QuestionType {
        MAIN,
        FOLLOW_UP
    }
}
