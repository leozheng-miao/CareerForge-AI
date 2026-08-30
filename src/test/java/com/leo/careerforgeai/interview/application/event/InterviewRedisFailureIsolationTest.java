package com.leo.careerforgeai.interview.application.event;

import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.interview.application.port.InterviewEventStore;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.interview.infrastructure.redis.event.RedisInterviewEventListener;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证Redis事件故障不影响MySQL事实读取且跨owner不会触达Redis
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
class InterviewRedisFailureIsolationTest {

    private static final ActorId OWNER = new ActorId("event-owner");
    private static final ActorId OTHER_OWNER = new ActorId("other-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("94000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-30T14:00:00Z");

    @Test
    void shouldReturnMysqlSnapshotWhenRedisReadIsUnavailable() {
        MockInterviewSessionRepository repository = mock(MockInterviewSessionRepository.class);
        InterviewEventStore eventStore = mock(InterviewEventStore.class);
        MockInterviewSession session = mock(MockInterviewSession.class);

        when(session.interviewId()).thenReturn(INTERVIEW_ID);
        when(repository.findById(OWNER, INTERVIEW_ID)).thenReturn(Optional.of(session));
        when(eventStore.readAfter(OWNER, INTERVIEW_ID, null, 100))
                .thenThrow(new RedisInfrastructureException(
                        RedisInfrastructureErrorType.UNAVAILABLE, "Redis不可用"
                ));

        InterviewEventQueryApplicationService service = new InterviewEventQueryApplicationService(
                () -> OWNER, repository, eventStore
        );
        InterviewEventObservation observation = service.observe(INTERVIEW_ID, null, 100);

        assertThat(observation.session()).isSameAs(session);
        assertThat(observation.events()).isEmpty();
        assertThat(observation.redisAvailable()).isFalse();
        assertThat(observation.redisErrorType()).isEqualTo(RedisInfrastructureErrorType.UNAVAILABLE);
    }

    @Test
    void shouldRejectOtherOwnerBeforeReadingRedis() {
        MockInterviewSessionRepository repository = mock(MockInterviewSessionRepository.class);
        InterviewEventStore eventStore = mock(InterviewEventStore.class);

        when(repository.findById(OTHER_OWNER, INTERVIEW_ID)).thenReturn(Optional.empty());

        InterviewEventQueryApplicationService service = new InterviewEventQueryApplicationService(
                () -> OTHER_OWNER, repository, eventStore
        );

        assertThatThrownBy(() -> service.observe(INTERVIEW_ID, null, 100))
                .isInstanceOf(MockInterviewNotFoundException.class);
        verifyNoInteractions(eventStore);
    }

    @Test
    void shouldSwallowRedisAppendFailureAfterMysqlCommit() {
        InterviewEventStore eventStore = mock(InterviewEventStore.class);
        InterviewEvent event = InterviewEvent.state(
                OWNER, INTERVIEW_ID, InterviewStatus.WAITING_FOR_ANSWER, NOW
        );

        when(eventStore.append(event)).thenThrow(new RedisInfrastructureException(
                RedisInfrastructureErrorType.TIMED_OUT, "Redis写入超时"
        ));

        RedisInterviewEventListener listener = new RedisInterviewEventListener(eventStore);

        assertThatCode(() -> listener.onCommitted(event)).doesNotThrowAnyException();
        verify(eventStore).append(event);
        verify(eventStore, times(1)).append(any());
    }
}