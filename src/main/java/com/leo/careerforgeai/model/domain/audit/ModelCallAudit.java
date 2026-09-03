package com.leo.careerforgeai.model.domain.audit;

import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.routing.ReasoningMode;

import java.time.Instant;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 保存一次真实供应商模型调用的安全耐久审计事实，不包含Prompt和模型输出。
 * @author: Miao Zheng
 * @date: 2026-09-03
 * @param auditId 审计记录ID
 * @param startedAt 供应商调用开始时间
 * @param taskType 后端业务任务类型
 * @param operationType 调用方式
 * @param providerId 供应商ID
 * @param modelProfile 路由Profile ID
 * @param providerModel 实际或计划调用的供应商模型名
 * @param routingVersion 路由策略版本
 * @param priceVersion 预留的价格配置版本
 * @param reasoningMode Thinking策略
 * @param fallbackCall 是否为Fallback调用
 * @param outcome 调用结果
 * @param errorCategory 稳定错误分类，成功时为空
 * @param providerRequestId 可用时记录的供应商请求ID
 * @param usage 供应商返回的Token事实，未知时为空
 * @param durationMs 供应商调用耗时
 * @param traceId 可用时记录的Trace ID
 * @param spanId 可用时记录的Span ID
 */
public record ModelCallAudit(
        UUID auditId, Instant startedAt, ModelTaskType taskType, OperationType operationType,
        String providerId, String modelProfile, String providerModel, String routingVersion,
        String priceVersion, ReasoningMode reasoningMode, boolean fallbackCall, Outcome outcome,
        String errorCategory, String providerRequestId, ModelUsage usage, long durationMs,
        String traceId, String spanId
) {
    public ModelCallAudit {
        if (auditId == null || startedAt == null || taskType == null || operationType == null
                || reasoningMode == null || outcome == null) throw new IllegalArgumentException("模型调用审计必填字段不能为null");
        providerId = requireText(providerId, "providerId", 64);
        modelProfile = requireText(modelProfile, "modelProfile", 64);
        providerModel = requireText(providerModel, "providerModel", 128);
        routingVersion = requireText(routingVersion, "routingVersion", 128);
        priceVersion = requireText(priceVersion, "priceVersion", 64);
        errorCategory = optionalText(errorCategory, "errorCategory", 64);
        providerRequestId = optionalText(providerRequestId, "providerRequestId", 128);
        traceId = optionalText(traceId, "traceId", 64);
        spanId = optionalText(spanId, "spanId", 32);
        if ((outcome == Outcome.SUCCESS) != (errorCategory == null)) throw new IllegalArgumentException("成功结果不能包含错误，失败结果必须包含错误");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs不能小于0");
        if (usage != null && (usage.inputTokens() < 0 || usage.outputTokens() < 0 || usage.totalTokens() < 0)) {
            throw new IllegalArgumentException("usage不能包含负数");
        }
    }

    public enum OperationType { CHAT, STREAM, TOOL_CALLING }
    public enum Outcome { SUCCESS, FAILURE }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = optionalText(value, field, maxLength);
        if (normalized == null) throw new IllegalArgumentException(field + "不能为空");
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + "格式非法");
        }
        return normalized;
    }
}