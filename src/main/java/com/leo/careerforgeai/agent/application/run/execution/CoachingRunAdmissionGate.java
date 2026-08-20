package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * @program: CareerForge-AI
 * @description: 使用全局和owner Semaphore对新Run执行进行零等待准入
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Component
public class CoachingRunAdmissionGate {

    private final Semaphore globalPermits;
    private final int permitsPerOwner;
    private final ConcurrentHashMap<ActorId, OwnerGate> ownerGates =
            new ConcurrentHashMap<>();

    public CoachingRunAdmissionGate(CoachingRunExecutionProperties properties) {
        Objects.requireNonNull(properties, "properties不能为空");
        this.globalPermits = new Semaphore(properties.maxConcurrentRuns(), true);
        this.permitsPerOwner = properties.maxConcurrentRunsPerOwner();
    }

    /*
    依次申请 全局 Semaphore 和 当前 owner 的 Semaphore
     */
    public Optional<RunAdmissionLease> tryAcquire(ActorId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        if (!globalPermits.tryAcquire()) return Optional.empty();

        OwnerGate ownerGate;
        try {
            ownerGate = retainOwnerGate(ownerId);
        } catch (RuntimeException exception) {
            globalPermits.release();
            throw exception;
        }

        if (!ownerGate.semaphore.tryAcquire()) {
            releaseOwnerReference(ownerId, ownerGate, false);
            globalPermits.release();
            return Optional.empty();
        }

        return Optional.of(new RunAdmissionLease(
                ownerId,
                () -> {
                    releaseOwnerReference(ownerId, ownerGate, true);
                    globalPermits.release();
                }
        ));
    }

    private OwnerGate retainOwnerGate(ActorId ownerId) {
        return ownerGates.compute(ownerId, (ignored, current) -> {
            OwnerGate retained = current == null
                    ? new OwnerGate(permitsPerOwner)
                    : current;
            retained.references++;
            return retained;
        });
    }

    private void releaseOwnerReference(
            ActorId ownerId,
            OwnerGate expected,
            boolean releasePermit
    ) {
        ownerGates.compute(ownerId, (ignored, current) -> {
            if (current != expected) {
                throw new IllegalStateException("owner执行许可状态不一致");
            }
            if (releasePermit) current.semaphore.release();

            current.references--;
            if (current.references < 0) {
                throw new IllegalStateException("owner执行许可引用计数小于0");
            }
            return current.references == 0 ? null : current;
        });
    }

    private static final class OwnerGate {

        private final Semaphore semaphore;
        private int references;

        private OwnerGate(int permits) {
            this.semaphore = new Semaphore(permits, true);
        }
    }
}