package com.leo.careerforgeai.model.exception.structured;

/**
 * @program: CareerForge-AI
 * @description: 定义结构化输出失败的稳定原因，禁止依赖Jackson异常文本作为业务分类
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
public enum StructuredOutputFailureReason {

    EMPTY_OR_OVERSIZED_CONTENT,
    MALFORMED_JSON,
    TRAILING_TOKEN,
    ROOT_NOT_OBJECT,
    UNKNOWN_FIELD,
    FIELD_TYPE_MISMATCH,
    INVALID_ENUM_VALUE,
    DTO_DESERIALIZATION_FAILED,
    OUTPUT_CONSTRAINT_VIOLATION,
    REFERENCE_NOT_ALLOWED,
    BUSINESS_INVARIANT_VIOLATION
}