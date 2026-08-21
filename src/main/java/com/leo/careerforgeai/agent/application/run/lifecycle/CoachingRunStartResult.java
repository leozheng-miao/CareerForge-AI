package com.leo.careerforgeai.agent.application.run.lifecycle;

import com.leo.careerforgeai.agent.domain.run.CoachingRun;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 返回Run启动结果并区分本次启动与已有状态重放
 * @author: Miao Zheng
 * @date: 2026-08-20
 * @param run 当前耐久Run
 * @param started 本次调用是否完成ACCEPTED到RUNNING迁移
 **/
public record CoachingRunStartResult(CoachingRun run, boolean started) {

    public CoachingRunStartResult {
        Objects.requireNonNull(run, "run不能为空");
    }
}