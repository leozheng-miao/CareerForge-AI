package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @program: CareerForge-AI
 * @description: 表示一次Run持有的全局和owner执行许可并保证只释放一次
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public final class RunAdmissionLease implements AutoCloseable {

    private final ActorId ownerId;
    private final Runnable releaser;
    private final AtomicBoolean released = new AtomicBoolean();

    RunAdmissionLease(ActorId ownerId, Runnable releaser) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId不能为空");
        this.releaser = Objects.requireNonNull(releaser, "releaser不能为空");
    }

    public ActorId ownerId() {
        return ownerId;
    }

    public boolean isReleased() {
        return released.get();
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) releaser.run();
    }
}