package com.leo.careerforgeai.interviewflow.api.query;

import com.leo.careerforgeai.interviewflow.application.query.InterviewQueryService.CurrentQuestion;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion.QuestionType;
import java.util.UUID;

public record CurrentQuestionResponse(UUID questionId, QuestionType type,
        int displayOrder, String content, String draft,
        long answerVersion, long sessionVersion) {
    public static CurrentQuestionResponse from(CurrentQuestion value) {
        return new CurrentQuestionResponse(value.question().questionId(),
                value.question().type(), value.question().displayOrder(),
                value.question().content(), value.draft(),
                value.answerVersion(), value.sessionVersion());
    }
}
