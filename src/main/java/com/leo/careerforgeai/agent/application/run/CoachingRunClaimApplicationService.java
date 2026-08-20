package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用MySQL唯一请求身份认领或重放Coaching Run并拒绝指纹冲突
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Service
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class CoachingRunClaimApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingRunRepository repository;
    private final CoachingRunRequestFingerprintService fingerprintService;
    private final Clock clock;

    public CoachingRunClaimApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingRunRepository repository,
            CoachingRunRequestFingerprintService fingerprintService,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(
                currentActorProvider,
                "currentActorProvider不能为空"
        );
        this.repository = Objects.requireNonNull(
                repository,
                "repository不能为空"
        );
        this.fingerprintService = Objects.requireNonNull(
                fingerprintService,
                "fingerprintService不能为空"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock不能为空"
        );
    }

    @Transactional
    public CoachingRunClaimResult claim(
            UUID sessionId,
            UUID requestId,
            long expectedSessionVersion,
            String message
    ) {
        Objects.requireNonNull(sessionId, "sessionId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");

        ActorId ownerId = currentActorProvider.currentActor();
        // 生成每个 run 的对应指纹，使用 sessionId， 期待版本 和 用户message
        String fingerprint = fingerprintService.fingerprint(
                sessionId,
                expectedSessionVersion,
                message
        );
        Instant now = clock.instant();

        //创建 receive 的 Run 并 落库
        CoachingRun candidate = CoachingRun.receive(
                UUID.randomUUID(),
                ownerId,
                sessionId,
                requestId,
                fingerprint,
                expectedSessionVersion,
                now
        );
        CoachingRun claimed = repository.claim(candidate);

        // 认领 数据库记录
        requireClaimIdentity(ownerId, requestId, claimed);

        if (!fingerprint.equals(claimed.requestFingerprint())) {
            throw new CoachingRunRequestConflictException(
                    claimed.runId()
            );
        }

        return new CoachingRunClaimResult(
                claimed,
                !candidate.runId().equals(claimed.runId())
        );
    }

    private void requireClaimIdentity(
            ActorId ownerId,
            UUID requestId,
            CoachingRun claimed
    ) {
        Objects.requireNonNull(claimed, "claimed Run不能为空");

        if (!ownerId.equals(claimed.ownerId())
                || !requestId.equals(claimed.requestId())) {
            throw new IllegalStateException(
                    "Repository返回了错误请求身份的Run"
            );
        }
    }
}