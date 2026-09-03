package com.leo.careerforgeai.model.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射model_call_audit模型调用安全审计表。
 * @author: Miao Zheng
 * @date: 2026-09-03
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("model_call_audit")
public class ModelCallAuditEntity {
    @TableId(value = "audit_id", type = IdType.INPUT)
    private String auditId;
    @TableField("started_at")
    private Instant startedAt;
    @TableField("task_type")
    private String taskType;
    @TableField("operation_type")
    private String operationType;
    @TableField("provider_id")
    private String providerId;
    @TableField("model_profile")
    private String modelProfile;
    @TableField("provider_model")
    private String providerModel;
    @TableField("routing_version")
    private String routingVersion;
    @TableField("price_version")
    private String priceVersion;
    @TableField("reasoning_mode")
    private String reasoningMode;
    @TableField("fallback_call")
    private Boolean fallbackCall;
    @TableField("outcome")
    private String outcome;
    @TableField("error_category")
    private String errorCategory;
    @TableField("provider_request_id")
    private String providerRequestId;
    @TableField("usage_status")
    private String usageStatus;
    @TableField("input_tokens")
    private Long inputTokens;
    @TableField("output_tokens")
    private Long outputTokens;
    @TableField("total_tokens")
    private Long totalTokens;
    @TableField("duration_ms")
    private Long durationMs;
    @TableField("trace_id")
    private String traceId;
    @TableField("span_id")
    private String spanId;
}
