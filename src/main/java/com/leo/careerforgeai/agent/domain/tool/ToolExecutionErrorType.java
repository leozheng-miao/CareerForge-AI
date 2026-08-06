package com.leo.careerforgeai.agent.domain.tool;

/** 定义允许返回给 Agent Loop 和模型的安全工具错误类型。 */
public enum ToolExecutionErrorType {
    UNKNOWN_TOOL,
    INVALID_ARGUMENTS,
    VALIDATION_FAILED,
    SCOPE_VIOLATION,
    TIMEOUT,
    EXECUTION_FAILED,
    OUTPUT_LIMIT_EXCEEDED
}