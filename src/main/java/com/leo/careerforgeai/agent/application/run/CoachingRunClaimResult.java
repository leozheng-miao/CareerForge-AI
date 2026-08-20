package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.domain.run.CoachingRun;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 返回新认领或幂等重放的Coaching Run
 * @author: Miao Zheng
 * @date: 2026-08-20
 * @param run MySQL中已经存在的耐久Run
 * @param replayed 是否为相同请求身份和指纹的幂等重放
 **/
public record CoachingRunClaimResult(
        CoachingRun run,
        boolean replayed
) {

    public CoachingRunClaimResult {
        Objects.requireNonNull(run, "run不能为空");
    }
}