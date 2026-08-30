package com.leo.careerforgeai.interview.infrastructure.redis.event;

import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.interview.application.event.InterviewEvent;
import com.leo.careerforgeai.interview.application.event.InterviewEventType;
import com.leo.careerforgeai.interview.application.port.InterviewEventStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 在MySQL提交后将模拟面试状态转换为一个或两个安全Redis事件
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Slf4j
@Component
public class RedisInterviewEventListener {

    private final InterviewEventStore eventStore;

    public RedisInterviewEventListener(InterviewEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore不能为空");
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onCommitted(InterviewEvent event) {
        if (!appendSafely(event)) return;

        InterviewEvent derived = derivedEvent(event);
        if (derived != null) appendSafely(derived);
    }

    private InterviewEvent derivedEvent(InterviewEvent event) {
        return switch (event.type()) {
            case QUESTION_READY -> event.derived(InterviewEventType.WAITING_FOR_ANSWER);
            case ANSWER_ACCEPTED -> event.derived(InterviewEventType.REVIEW_STARTED);
            case REPORT_READY -> event.derived(InterviewEventType.WAITING_FOR_CONFIRMATION);
            default -> null;
        };
    }

    private boolean appendSafely(InterviewEvent event) {
        try {
            String eventId = eventStore.append(event);
            log.debug("面试安全事件已写入Redis，interviewId={}, type={}, status={}, eventId={}",
                    event.interviewId(), event.type(), event.status(), eventId);
            return true;
        } catch (RedisInfrastructureException exception) {
            log.warn("面试安全事件写入Redis失败，interviewId={}, type={}, errorType={}",
                    event.interviewId(), event.type(), exception.errorType());
            return false;
        } catch (RuntimeException exception) {
            log.error("面试安全事件写入发生未知异常，interviewId={}, type={}, errorType={}",
                    event.interviewId(), event.type(), exception.getClass().getSimpleName());
            return false;
        }
    }
}