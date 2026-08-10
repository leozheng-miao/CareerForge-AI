package com.leo.careerforgeai.agent.infrastructure.mcp.client;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表达独立MCP Client生命周期和协议操作的固定脱敏错误。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public final class McpInteropClientException extends RuntimeException {

    private final ErrorType errorType;

    McpInteropClientException(ErrorType errorType, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
    }

    public ErrorType errorType() {
        return errorType;
    }

    /** 当前Client边界能够稳定识别的错误类型。 */
    public enum ErrorType {
        NOT_INITIALIZED,
        CLOSED,
        PROTOCOL_INCOMPATIBLE,
        TIMEOUT,
        INITIALIZATION_FAILED,
        TOOL_LIST_FAILED,
        TOOL_CALL_FAILED,
        CLOSE_FAILED
    }
}