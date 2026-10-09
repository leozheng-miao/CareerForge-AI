package com.leo.careerforgeai.interviewflow.api.query;

import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InterviewHistoryPageResponse(List<Item> items, String nextCursor) {
    public record Item(UUID interviewId, InterviewMode mode, SessionStage stage,
                       int submittedAnswerCount, Instant createdAt, long version) {
        public static Item from(InterviewAggregate value) {
            return new Item(value.session().interviewId(), value.session().mode(),
                    value.session().stage(), value.submittedAnswerCount(),
                    value.createdAt(), value.session().version());
        }
    }
}
