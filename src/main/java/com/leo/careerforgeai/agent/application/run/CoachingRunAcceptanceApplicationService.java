package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在同一短事务中保存USER Turn并将Coaching Run推进到ACCEPTED
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunAcceptanceApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingRunRepository repository;
    private final CoachingRunRequestFingerprintService fingerprintService;
    private final CoachingSessionApplicationService sessionApplicationService;
    private final Clock clock;

    public CoachingRunAcceptanceApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingRunRepository repository,
            CoachingRunRequestFingerprintService fingerprintService,
            CoachingSessionApplicationService sessionApplicationService,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.fingerprintService = Objects.requireNonNull(fingerprintService, "fingerprintService不能为空");
        this.sessionApplicationService = Objects.requireNonNull(sessionApplicationService, "sessionApplicationService不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public CoachingRun accept(UUID runId, String message) {
        Objects.requireNonNull(runId, "runId不能为空");

        ActorId ownerId = currentActorProvider.currentActor();
        CoachingRun current = repository.findByRunId(ownerId, runId)
                .orElseThrow(() -> new IllegalArgumentException("Run不存在或不属于当前用户"));

        String fingerprint = fingerprintService.fingerprint(
                current.sessionId(),
                current.expectedSessionVersion(),
                message
        );
        // 验证指纹，不对直接返回
        if (!fingerprint.equals(current.requestFingerprint())) {
            throw new CoachingRunRequestConflictException(current.runId());
        }
        // 若 这个 run 的当前状态不是 received，证明已经走过这一步无需再accept，直接返回
        if (current.status() != CoachingRunStatus.RECEIVED) {
            return current;
        }

        ConversationTurn userTurn = sessionApplicationService.recordUserTurn(
                current.sessionId(),
                current.expectedSessionVersion(),
                message
        );
        if (!ownerId.equals(userTurn.ownerId()) || !current.sessionId().equals(userTurn.sessionId())) {
            throw new IllegalStateException("保存的USER Turn与Run身份不一致");
        }

        CoachingRun accepted = current.accept(userTurn.turnId(), clock.instant());
        if (!repository.updateIfVersionMatches(ownerId, accepted, current.version())) {
            throw new CoachingRunVersionConflictException(current.runId(), current.version());
        }
        return accepted;
    }
}