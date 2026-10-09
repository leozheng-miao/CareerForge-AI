package com.leo.careerforgeai.interviewflow;

import com.leo.careerforgeai.interviewflow.application.port.*;
import com.leo.careerforgeai.interviewflow.application.session.*;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Tag;import org.junit.jupiter.api.Test;
import java.time.*;import java.util.*;import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;import static org.mockito.ArgumentMatchers.any;import static org.mockito.Mockito.*;

@Tag("integration")
class OwnershipAndIdempotencyIT {
    @Test void duplicateCreateReturnsOneSessionAndCrossOwnerIsHidden() {
        AtomicReference<ActorId> actor=new AtomicReference<>(new ActorId("owner-a"));
        InterviewSessionRepository sessions=mock(InterviewSessionRepository.class);InterviewInputSnapshotRepository inputs=mock(InterviewInputSnapshotRepository.class);InterviewTaskRepository tasks=mock(InterviewTaskRepository.class);
        UUID request=UUID.randomUUID();AtomicReference<InterviewAggregate> stored=new AtomicReference<>();
        when(sessions.findByRequestId(any(),eq(request))).thenAnswer(call->Optional.ofNullable(stored.get()).filter(value->value.session().ownerId().equals(call.getArgument(0))));
        when(sessions.findInProgress(any())).thenReturn(Optional.empty());when(sessions.claim(any())).thenAnswer(call->{var value=call.getArgument(0,InterviewSessionRepository.SessionClaim.class).candidate();stored.compareAndSet(null,value);return stored.get();});when(tasks.enqueue(any())).thenReturn(true);
        var service=new InterviewSessionApplicationService(actor::get,sessions,inputs,tasks,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        var command=new CreateQuickInterviewCommand(request,CreateQuickInterviewCommand.AI_AGENT_DEVELOPMENT);
        var first=service.createQuick(command);var replay=service.createQuick(command);
        assertThat(replay.interviewId()).isEqualTo(first.interviewId());verify(inputs,times(1)).insert(any());verify(tasks,times(1)).enqueue(any());
        actor.set(new ActorId("owner-b"));when(sessions.findById(actor.get(),first.interviewId())).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.get(first.interviewId())).isInstanceOf(InterviewFlowNotFoundException.class);
    }
}
