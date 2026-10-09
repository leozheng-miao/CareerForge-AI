package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Redis中只保存可公开的进度事件，不保存题干、答案或报告正文。 */
public interface InterviewEventPublisher {
    void publish(SafeInterviewEvent event);

    List<SafeInterviewEvent> replay(ActorId ownerId, UUID interviewId,
                                    String afterEventId, int limit);

    AutoCloseable subscribe(ActorId ownerId, UUID interviewId,
                            Consumer<SafeInterviewEvent> consumer);

    record SafeInterviewEvent(String eventId, UUID interviewId,
                              ActorId ownerId, EventType type,
                              String resourceId, String status,
                              long version, Instant occurredAt) {
        public SafeInterviewEvent {
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("eventId不能为空");
            }
            java.util.Objects.requireNonNull(interviewId);
            java.util.Objects.requireNonNull(ownerId);
            java.util.Objects.requireNonNull(type);
            java.util.Objects.requireNonNull(occurredAt);
        }
    }

    enum EventType {
        PREPARATION_READY, QUESTION_READY, REPORT_READY, RETRYABLE_FAILURE
    }
}
