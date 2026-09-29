package com.leo.careerforgeai.interviewflow.domain.session;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 返回状态机迁移后的新聚合及是否需要创建报告任务
 * @author: Miao Zheng
 * @date: 2026-09-30
 * @param aggregate 迁移后的不可变聚合
 * @param shouldGenerateReport 是否需要由应用层创建报告任务
 */
public record TransitionResult(
        InterviewAggregate aggregate,
        boolean shouldGenerateReport
) {

    public TransitionResult {
        Objects.requireNonNull(aggregate, "aggregate不能为空");
        if (shouldGenerateReport
                && aggregate.session().stage()
                != SessionStage.GENERATING_REPORT) {
            throw new IllegalArgumentException(
                    "只有进入GENERATING_REPORT时才能请求生成报告"
            );
        }
    }

    public static TransitionResult withoutReport(
            InterviewAggregate aggregate
    ) {
        return new TransitionResult(aggregate, false);
    }

    public static TransitionResult withReport(
            InterviewAggregate aggregate
    ) {
        return new TransitionResult(aggregate, true);
    }
}
