package com.leo.careerforgeai.agent.infrastructure.mcp.client;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.CLOSED;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.CLOSE_FAILED;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.INITIALIZATION_FAILED;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.NOT_INITIALIZED;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.TOOL_CALL_FAILED;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.TOOL_LIST_FAILED;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.PROTOCOL_INCOMPATIBLE;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.TIMEOUT;

/**
 * @program: CareerForge-AI
 * @description: 强制显式初始化并执行MCP能力协商、工具发现和工具调用的独立同步Client。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public final class McpInteropClient implements AutoCloseable {

    private final McpSyncClient delegate;
    private volatile boolean initialized;
    private volatile boolean closed;
    private volatile McpSchema.InitializeResult initializationResult;

    McpInteropClient(McpSyncClient delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate不能为空");
    }

    /** 显式完成协议版本、能力和实现信息协商，不依赖SDK的延迟初始化。 */
    public synchronized McpSchema.InitializeResult initialize() {
        ensureOpen();
        if (initialized) return initializationResult;

        try {
            McpSchema.InitializeResult result = delegate.initialize();
            validateInitializationResult(result);
            initializationResult = result;
            initialized = true;
            return result;
        } catch (RuntimeException exception) {
            throw initializationFailure(exception);
        }
    }

    /** 返回已经完成的初始化结果，用于检查协议版本和服务端能力。 */
    public McpSchema.InitializeResult initializationResult() {
        ensureReady();
        return initializationResult;
    }

    /** 通过MCP tools/list读取服务端实际公开的工具。 */
    public McpSchema.ListToolsResult listTools() {
        ensureReady();

        try {
            McpSchema.ListToolsResult result = delegate.listTools();
            if (result == null) throw new IllegalStateException("tools/list返回空结果");
            return result;
        } catch (RuntimeException exception) {
            throw operationFailure(
                    TOOL_LIST_FAILED,
                    "MCP工具列表读取失败",
                    exception
            );
        }
    }

    /** 通过MCP tools/call调用指定工具并完整保留协议结果。 */
    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        ensureReady();

        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName不能为空");
        }
        Objects.requireNonNull(arguments, "arguments不能为空");

        Map<String, Object> copiedArguments = Collections.unmodifiableMap(
                new LinkedHashMap<>(arguments)
        );
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder(toolName)
                .arguments(copiedArguments)
                .build();

        try {
            McpSchema.CallToolResult result = delegate.callTool(request);
            if (result == null) throw new IllegalStateException("tools/call返回空结果");
            return result;
        } catch (RuntimeException exception) {
            throw operationFailure(TOOL_CALL_FAILED, "MCP工具调用失败", exception);
        }
    }

    public boolean isInitialized() {
        return initialized && !closed;
    }

    /** 关闭底层Client且不向调用方泄露SDK内部异常信息。 */
    @Override
    public synchronized void close() {
        if (closed) return;

        try {
            delegate.close();
        } catch (RuntimeException exception) {
            throw failure(CLOSE_FAILED, "MCP Client关闭失败", exception);
        } finally {
            initialized = false;
            closed = true;
        }
    }

    /** 拒绝SDK可能提供的隐式初始化路径。 */
    private void ensureReady() {
        ensureOpen();
        if (!initialized) {
            throw failure(NOT_INITIALIZED, "MCP Client尚未完成初始化", null);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw failure(CLOSED, "MCP Client已经关闭", null);
        }
    }

    /** 对反序列化后的必要初始化字段进行二次防御。 */
    private void validateInitializationResult(McpSchema.InitializeResult result) {
        if (result == null
                || result.protocolVersion() == null
                || result.protocolVersion().isBlank()
                || result.capabilities() == null
                || result.serverInfo() == null
                || result.serverInfo().name().isBlank()
                || result.serverInfo().version().isBlank()) {
            throw new IllegalStateException("初始化结果缺少必要字段");
        }
    }

    /** 根据SDK实际异常链区分协议不兼容、超时和普通初始化失败。 */
    private McpInteropClientException initializationFailure(RuntimeException exception) {
        if (isProtocolIncompatible(exception)) {
            return failure(
                    PROTOCOL_INCOMPATIBLE,
                    "MCP协议版本不兼容",
                    exception
            );
        }

        if (hasTimeoutCause(exception)) {
            return failure(
                    TIMEOUT,
                    "MCP初始化超时",
                    exception
            );
        }

        return failure(
                INITIALIZATION_FAILED,
                "MCP Client初始化失败",
                exception
        );
    }

    /** 为初始化后的协议操作统一识别超时，同时保留操作自身错误类型。 */
    private McpInteropClientException operationFailure(
            McpInteropClientException.ErrorType defaultType,
            String defaultMessage,
            RuntimeException exception
    ) {
        if (hasTimeoutCause(exception)) {
            return failure(
                    TIMEOUT,
                    "MCP请求超时",
                    exception
            );
        }

        return failure(defaultType, defaultMessage, exception);
    }

    /** 识别SDK 2.0.0在版本协商失败时产生的McpError。 */
    private boolean isProtocolIncompatible(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof McpError mcpError
                    && mcpError.getJsonRpcError() != null
                    && Integer.valueOf(McpSchema.ErrorCodes.INVALID_PARAMS)
                    .equals(mcpError.getJsonRpcError().code())
                    && "Unsupported protocol version"
                    .equals(mcpError.getJsonRpcError().message())) {
                return true;
            }

            if (current.getCause() == current) break;
            current = current.getCause();
        }

        return false;
    }

    /** 识别SDK请求边界、JDK HttpClient和Socket层的超时异常。 */
    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return true;
            }

            if (current.getCause() == current) break;
            current = current.getCause();
        }

        return false;
    }

    private McpInteropClientException failure(
            McpInteropClientException.ErrorType errorType,
            String safeMessage,
            Throwable cause
    ) {
        return new McpInteropClientException(errorType, safeMessage, cause);
    }
}