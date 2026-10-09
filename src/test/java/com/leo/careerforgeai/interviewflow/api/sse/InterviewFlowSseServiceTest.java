package com.leo.careerforgeai.interviewflow.api.sse;

import com.leo.careerforgeai.interviewflow.application.port.InterviewEventPublisher;
import com.leo.careerforgeai.interviewflow.application.query.InterviewQueryService;
import com.leo.careerforgeai.interviewflow.domain.session.*;
import com.leo.careerforgeai.interviewflow.domain.timer.InterviewTimer;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.*;import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;import static org.mockito.Mockito.*;

class InterviewFlowSseServiceTest {
    @Test void reconnectSendsMysqlSnapshotAndReplaysOnlySafeEvents() throws IOException {
        ActorId owner=new ActorId("owner");UUID id=UUID.randomUUID();
        InterviewQueryService queries=mock(InterviewQueryService.class); InterviewEventPublisher events=mock(InterviewEventPublisher.class);
        var aggregate=new InterviewAggregate(new InterviewSession(id,owner,InterviewMode.QUICK,SessionStage.INTERVIEWING,7),InterviewTimer.notStarted(Duration.ofHours(1)),2,0,0,Instant.EPOCH);
        when(queries.get(id)).thenReturn(aggregate);when(events.replay(owner,id,"e-1",100)).thenReturn(List.of(new InterviewEventPublisher.SafeInterviewEvent("e-2",id,owner,InterviewEventPublisher.EventType.QUESTION_READY,UUID.randomUUID().toString(),"READY",7,Instant.EPOCH)));
        when(events.subscribe(eq(owner),eq(id),any())).thenReturn(()->{});
        var service=new InterviewFlowSseService(()->owner,queries,events,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        try(MockedConstruction<SseEmitter> construction=mockConstruction(SseEmitter.class)){
            assertThat(service.open(id,"e-1")).isSameAs(construction.constructed().getFirst());
            verify(construction.constructed().getFirst(),times(2)).send(any(SseEmitter.SseEventBuilder.class));
            verify(events).replay(owner,id,"e-1",100);
        }
    }
}
