package com.leo.careerforgeai.agent.application.run.event;

import com.leo.careerforgeai.agent.application.loop.AgentLoopObserver;
import com.leo.careerforgeai.agent.application.port.run.CoachingRunEventStore;
import com.leo.careerforgeai.agent.application.tool.AgentTool;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证白名单工具事件映射、未知工具隔离和Redis失败不影响Agent
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class CoachingRunProgressEventPublisherTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID RUN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-23T04:00:00Z");
    private static final String TOOL_NAME = "search_career_materials";

    @Mock
    private CoachingRunEventStore eventStore;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private AgentTool<?, ?> registeredTool;

    private CoachingRunProgressEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CoachingRunProgressEventPublisher(
                eventStore,
                toolRegistry
        );
    }

    @Test
    void shouldAppendStartedAndCompletedEventsForAllowedTool() {
        when(toolRegistry.find(TOOL_NAME))
                .thenReturn(Optional.of(registeredTool));
        when(eventStore.append(any(CoachingRunEvent.class)))
                .thenReturn("100-0", "101-0");

        AgentLoopObserver observer = publisher.observerFor(OWNER, RUN_ID);
        observer.toolStarted(TOOL_NAME, NOW);
        observer.toolCompleted(
                TOOL_NAME,
                ToolExecutionStatus.SUCCESS,
                NOW.plusSeconds(1)
        );

        ArgumentCaptor<CoachingRunEvent> captor =
                ArgumentCaptor.forClass(CoachingRunEvent.class);
        org.mockito.Mockito.verify(eventStore, org.mockito.Mockito.times(2))
                .append(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(CoachingRunEvent::type)
                .containsExactly(
                        CoachingRunEventType.TOOL_STARTED,
                        CoachingRunEventType.TOOL_COMPLETED
                );
        assertThat(captor.getAllValues())
                .extracting(CoachingRunEvent::toolName)
                .containsExactly(TOOL_NAME, TOOL_NAME);
        assertThat(captor.getAllValues().getFirst().toolStatus()).isNull();
        assertThat(captor.getAllValues().get(1).toolStatus())
                .isEqualTo(ToolExecutionStatus.SUCCESS);
        assertThat(captor.getAllValues())
                .allSatisfy(event -> {
                    assertThat(event.ownerId()).isEqualTo(OWNER);
                    assertThat(event.runId()).isEqualTo(RUN_ID);
                });
    }

    @Test
    void shouldDiscardUnknownToolWithoutWritingRedis() {
        when(toolRegistry.find("unknown_tool"))
                .thenReturn(Optional.empty());

        AgentLoopObserver observer = publisher.observerFor(OWNER, RUN_ID);
        observer.toolStarted("unknown_tool", NOW);
        observer.toolCompleted(
                "unknown_tool",
                ToolExecutionStatus.FAILURE,
                NOW.plusSeconds(1)
        );

        verifyNoInteractions(eventStore);
    }

    @Test
    void shouldNotPropagateRedisFailureToAgentLoop() {
        when(toolRegistry.find(TOOL_NAME))
                .thenReturn(Optional.of(registeredTool));
        when(eventStore.append(any(CoachingRunEvent.class)))
                .thenThrow(new RedisInfrastructureException(
                        RedisInfrastructureErrorType.UNAVAILABLE,
                        "Redis不可用"
                ));

        AgentLoopObserver observer = publisher.observerFor(OWNER, RUN_ID);

        assertThatCode(() -> observer.toolStarted(TOOL_NAME, NOW))
                .doesNotThrowAnyException();
    }
}