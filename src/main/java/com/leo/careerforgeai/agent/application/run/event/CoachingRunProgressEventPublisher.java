package com.leo.careerforgeai.agent.application.run.event;

import com.leo.careerforgeai.agent.application.loop.AgentLoopObserver;
import com.leo.careerforgeai.agent.application.port.run.CoachingRunEventStore;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.shared.actor.ActorId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 将异步Run的白名单工具观察结果转换为安全Redis事件
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunProgressEventPublisher {

    private final CoachingRunEventStore eventStore;
    private final ToolRegistry toolRegistry;

    public CoachingRunProgressEventPublisher(
            CoachingRunEventStore eventStore,
            ToolRegistry toolRegistry
    ) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore不能为空");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry不能为空");
    }

    public AgentLoopObserver observerFor(ActorId ownerId, UUID runId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(runId, "runId不能为空");

        return new AgentLoopObserver() {
            @Override
            public void toolStarted(String toolName, Instant occurredAt) {
                if (!isAllowedTool(toolName)) return;
                appendSafely(CoachingRunEvent.toolStarted(
                        ownerId,
                        runId,
                        toolName,
                        occurredAt
                ));
            }

            @Override
            public void toolCompleted(
                    String toolName,
                    ToolExecutionStatus status,
                    Instant occurredAt
            ) {
                if (!isAllowedTool(toolName)) return;
                appendSafely(CoachingRunEvent.toolCompleted(
                        ownerId,
                        runId,
                        toolName,
                        status,
                        occurredAt
                ));
            }
        };
    }

    private boolean isAllowedTool(String toolName) {
        if (toolRegistry.find(toolName).isPresent()) return true;
        log.warn("Run工具观察事件因工具不在白名单中被丢弃");
        return false;
    }

    private void appendSafely(CoachingRunEvent event) {
        try {
            String eventId = eventStore.append(event);
            log.debug(
                    "Run工具事件已写入Redis，runId={}, type={}, toolName={}, eventId={}",
                    event.runId(),
                    event.type(),
                    event.toolName(),
                    eventId
            );
        } catch (RedisInfrastructureException exception) {
            log.warn(
                    "Run工具事件写入Redis失败，runId={}, type={}, errorType={}",
                    event.runId(),
                    event.type(),
                    exception.errorType()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Run工具事件写入发生未知异常，runId={}, type={}, errorType={}",
                    event.runId(),
                    event.type(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}