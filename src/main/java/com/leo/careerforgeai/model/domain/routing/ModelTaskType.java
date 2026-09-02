// ModelTaskType.java
package com.leo.careerforgeai.model.domain.routing;

/**
 * @program: CareerForge-AI
 * @description: 定义只能由Java业务代码选择的稳定模型任务类型。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
public enum ModelTaskType {
    JOB_REQUIREMENTS_EXTRACTION,
    CAREER_COACH,
    MEMORY_EXTRACTION,
    TRAINING_PLAN_GENERATION,
    RAG_ANSWER,
    RAG_RERANK,
    INTERVIEW_QUESTION,
    INTERVIEW_TECHNICAL_REVIEW,
    INTERVIEW_EVIDENCE_REVIEW,
    INTERVIEW_REPORT,
    STRUCTURED_OUTPUT_REPAIR,
    EMBEDDING
}