package com.leo.careerforgeai.interview.api.dto.event;

/**
 * @program: CareerForge-AI
 * @description: 标识模拟面试SSE数据来自Redis短期事件或MySQL权威快照
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
public enum InterviewSseEventSource {

    REDIS_STREAM,
    MYSQL_STATE_SNAPSHOT
}