package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 应用启动后将上次进程遗留的非终态Coaching Run恢复为INTERRUPTED
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Component
@Slf4j
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunStartupRecovery {

    private static final int BATCH_SIZE = 100;
    private static final String FAILURE_CODE = "APPLICATION_RESTARTED";

    private final CoachingRunRepository repository;
    private final CoachingRunInterruptionApplicationService interruptionService;
    private final Instant recoveryCutoff;

    public CoachingRunStartupRecovery(
            CoachingRunRepository repository,
            CoachingRunInterruptionApplicationService interruptionService,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.interruptionService = Objects.requireNonNull(interruptionService, "interruptionService不能为空");
        this.recoveryCutoff = Objects.requireNonNull(clock, "clock不能为空").instant();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        int recoveredTotal = 0;

        while (true) {
            List<CoachingRun> candidates = repository.findNonTerminalUpdatedBefore(
                    recoveryCutoff,
                    BATCH_SIZE
            );
            if (candidates.isEmpty()) break;

            int recoveredInBatch = 0;
            for (CoachingRun candidate : candidates) {
                if (recover(candidate)) recoveredInBatch++;
            }

            recoveredTotal += recoveredInBatch;
            if (recoveredInBatch == 0) {
                log.error(
                        "Coaching Run启动恢复无法继续，cutoff={}, candidateCount={}",
                        recoveryCutoff,
                        candidates.size()
                );
                break;
            }
        }

        log.info(
                "Coaching Run启动恢复完成，cutoff={}, recoveredCount={}",
                recoveryCutoff,
                recoveredTotal
        );
    }

    private boolean recover(CoachingRun candidate) {
        try {
            interruptionService.interruptForActor(
                    candidate.ownerId(),
                    candidate.runId(),
                    FAILURE_CODE
            );
            return true;
        } catch (CoachingRunVersionConflictException exception) {
            log.info(
                    "Coaching Run恢复时状态已被其他执行者更新，runId={}, expectedVersion={}",
                    exception.runId(),
                    exception.expectedVersion()
            );
            return false;
        } catch (RuntimeException exception) {
            log.error(
                    "Coaching Run启动恢复失败，runId={}, errorType={}",
                    candidate.runId(),
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }
}