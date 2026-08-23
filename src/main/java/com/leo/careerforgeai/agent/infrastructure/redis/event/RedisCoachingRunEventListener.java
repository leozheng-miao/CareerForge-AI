package com.leo.careerforgeai.agent.infrastructure.redis.event;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunEventStore;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEvent;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 在MySQL提交后向Redis追加白名单安全Run事件
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@Component
@Slf4j
public class RedisCoachingRunEventListener {

    private final CoachingRunEventStore eventStore;

    public RedisCoachingRunEventListener(CoachingRunEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore不能为空");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(CoachingRunEvent event) {
        if (event.type() == CoachingRunEventType.RUN_COMPLETED) {
            if (!appendSafely(event.answerReady())) return;
        }
        appendSafely(event);
    }

    private boolean appendSafely(CoachingRunEvent event) {
        try {
            String eventId = eventStore.append(event);
            log.debug(
                    "Run安全事件已写入Redis，runId={}, type={}, status={}, eventId={}",
                    event.runId(),
                    event.type(),
                    event.status(),
                    eventId
            );
            return true;
        } catch (RedisInfrastructureException exception) {
            log.warn(
                    "Run安全事件写入Redis失败，runId={}, type={}, errorType={}",
                    event.runId(),
                    event.type(),
                    exception.errorType()
            );
            return false;
        } catch (RuntimeException exception) {
            log.error(
                    "Run安全事件写入发生未知异常，runId={}, type={}, errorType={}",
                    event.runId(),
                    event.type(),
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }
}