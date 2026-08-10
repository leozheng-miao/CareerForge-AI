package com.leo.careerforgeai.agent.domain.tool.career.search;

/**
 * @program: CareerForge-AI
 * @description: 表示允许暴露给模型的职业材料搜索失败分类。
 * @author: Miao Zheng
 * @date: 2026-08-06 21:00
 **/
public enum SearchCareerMaterialsErrorType {
    RETRIEVAL_FAILED,
    UPSTREAM_TIMEOUT,
    AGENT_DEADLINE_EXCEEDED,
    INTERNAL_ERROR
}