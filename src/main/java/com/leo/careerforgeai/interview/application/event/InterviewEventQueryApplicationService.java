package com.leo.careerforgeai.interview.application.event;

import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.interview.application.port.InterviewEventStore;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 先校验MySQL面试归属，再读取可丢失的Redis短期安全事件
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewEventQueryApplicationService {

    private static final int MAX_LIMIT = 1_000;

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewEventStore eventStore;

    public InterviewEventQueryApplicationService(CurrentActorProvider currentActorProvider,
                                                 MockInterviewSessionRepository sessionRepository,
                                                 InterviewEventStore eventStore) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore不能为空");
    }

    public InterviewEventObservation observe(UUID interviewId, String lastEventId, int limit) {
        return observeForActor(
                currentActorProvider.currentActor(),
                interviewId,
                lastEventId,
                limit
        );
    }

    public InterviewEventObservation observeForActor(
            ActorId ownerId,
            UUID interviewId,
            String lastEventId,
            int limit
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit必须在1到1000之间");
        }

        MockInterviewSession session = sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));

        try {
            return new InterviewEventObservation(
                    session,
                    eventStore.readAfter(ownerId, interviewId, lastEventId, limit),
                    null
            );
        } catch (RedisInfrastructureException exception) {
            return new InterviewEventObservation(
                    session,
                    List.of(),
                    exception.errorType()
            );
        }
    }
}