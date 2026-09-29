package com.leo.careerforgeai.interviewflow.domain.turn;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示一道题唯一的可保存草稿和冻结答案
 * @author: Miao Zheng
 * @date: 2026-09-30
 * @param answerId 答案ID
 * @param questionId 所属问题ID
 * @param draftContent 最后一次服务端确认的草稿
 * @param submittedContent 已冻结的正式答案
 * @param status 当前生命周期状态
 */
public record InterviewAnswer(
        UUID answerId,
        UUID questionId,
        String draftContent,
        String submittedContent,
        AnswerStatus status
) {

    public InterviewAnswer {
        Objects.requireNonNull(answerId, "answerId不能为空");
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        draftContent = draftContent == null ? "" : draftContent;
        validateSubmittedContent(submittedContent, status);
    }

    public static InterviewAnswer draft(
            UUID answerId,
            UUID questionId
    ) {
        return new InterviewAnswer(
                answerId,
                questionId,
                "",
                null,
                AnswerStatus.DRAFT
        );
    }

    public InterviewAnswer saveDraft(String content) {
        requireStatus(AnswerStatus.DRAFT, "只有草稿状态可以保存回答");
        Objects.requireNonNull(content, "content不能为空");
        return new InterviewAnswer(
                answerId,
                questionId,
                content,
                null,
                AnswerStatus.DRAFT
        );
    }

    public InterviewAnswer submit() {
        requireStatus(AnswerStatus.DRAFT, "只有草稿状态可以提交回答");
        if (draftContent.isBlank()) {
            throw new IllegalStateException("空回答不能提交，请使用跳过操作");
        }
        return new InterviewAnswer(
                answerId,
                questionId,
                draftContent,
                draftContent,
                AnswerStatus.SUBMITTED
        );
    }

    public InterviewAnswer startAnalysis() {
        requireStatus(AnswerStatus.SUBMITTED, "只有已提交答案可以开始分析");
        return withStatus(AnswerStatus.ANALYZING);
    }

    public InterviewAnswer close() {
        if (status != AnswerStatus.SUBMITTED && status != AnswerStatus.ANALYZING) {
            throw new IllegalStateException("只有已提交或分析中的答案可以关闭");
        }
        return withStatus(AnswerStatus.CLOSED);
    }

    public InterviewAnswer discard() {
        requireStatus(AnswerStatus.DRAFT, "只有未提交草稿可以丢弃");
        return new InterviewAnswer(
                answerId,
                questionId,
                draftContent,
                null,
                AnswerStatus.DISCARDED
        );
    }

    private InterviewAnswer withStatus(AnswerStatus nextStatus) {
        return new InterviewAnswer(
                answerId,
                questionId,
                draftContent,
                submittedContent,
                nextStatus
        );
    }

    private void requireStatus(AnswerStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private static void validateSubmittedContent(
            String submittedContent,
            AnswerStatus status
    ) {
        boolean requiresSubmittedContent = status == AnswerStatus.SUBMITTED
                || status == AnswerStatus.ANALYZING
                || status == AnswerStatus.CLOSED;
        if (requiresSubmittedContent
                && (submittedContent == null || submittedContent.isBlank())) {
            throw new IllegalArgumentException("已提交答案正文不能为空");
        }
        if (!requiresSubmittedContent && submittedContent != null) {
            throw new IllegalArgumentException("未提交答案不能包含冻结正文");
        }
    }
}
