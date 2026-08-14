package com.leo.careerforgeai.memory.application.extraction.dto;

/**
 * @program: CareerForge-AI
 * @description: 标识Memory提取失败发生的稳定业务阶段，供安全诊断和重试决策使用
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
public enum MemoryExtractionFailureStage {

    SOURCE_INPUT_VALIDATION,
    INPUT_SERIALIZATION,
    MODEL_INVOCATION,
    RESPONSE_ENVELOPE_VALIDATION,
    JSON_PARSING,
    OUTPUT_STRUCTURE_VALIDATION,
    SOURCE_REFERENCE_VALIDATION,
    SENSITIVE_CONTENT_VALIDATION,
    CANDIDATE_BUSINESS_VALIDATION,
    PERSISTENCE_BOUNDARY_VALIDATION
}