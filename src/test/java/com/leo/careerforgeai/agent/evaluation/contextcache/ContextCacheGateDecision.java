package com.leo.careerforgeai.agent.evaluation.contextcache;

/**
 * @program: CareerForge-AI
 * @description: 表示Context缓存候选在版本与命中机会预检查后的决策
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
public enum ContextCacheGateDecision {

    REJECT_NO_VERSION_REUSE,
    REJECT_INCOMPLETE_VERSION_COVERAGE,
    REQUIRE_REAL_MYSQL_REDIS_SMOKE
}