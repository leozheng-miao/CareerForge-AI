package com.leo.careerforgeai.interviewflow.domain.timer;

/**
 * @program: CareerForge-AI
 * @description: 定义有效作答时间的独立运行状态
 * @author: Miao Zheng
 * @date: 2026-09-30
 */
public enum TimerState {

    NOT_STARTED,
    RUNNING,
    PAUSED,
    EXHAUSTED,
    STOPPED
}
