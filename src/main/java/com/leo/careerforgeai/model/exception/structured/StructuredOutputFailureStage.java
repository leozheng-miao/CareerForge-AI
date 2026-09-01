package com.leo.careerforgeai.model.exception.structured;

/**
 * @program: CareerForge-AI
 * @description: 标识结构化模型输出在Java可信化链路中的最早失败阶段
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
public enum StructuredOutputFailureStage {

    CONTENT_BOUNDARY_VALIDATION,
    JSON_PARSING,
    DTO_DESERIALIZATION,
    OUTPUT_STRUCTURE_VALIDATION,
    REFERENCE_VALIDATION,
    BUSINESS_CONTRACT_VALIDATION
}