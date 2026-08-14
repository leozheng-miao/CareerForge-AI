package com.leo.careerforgeai.memory.infrastructure.persistence.entity;

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
 * @description: 映射memory_extraction_receipt成功提取凭证表
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("memory_extraction_receipt")
public class MemoryExtractionReceiptEntity {

    @TableId(value = "receipt_id", type = IdType.INPUT)
    private String receiptId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("session_id")
    private String sessionId;

    @TableField("extractor_version")
    private String extractorVersion;

    @TableField("input_fingerprint")
    private String inputFingerprint;

    @TableField("source_refs_json")
    private String sourceRefsJson;

    @TableField("memory_ids_json")
    private String memoryIdsJson;

    @TableField("model_request_id")
    private String modelRequestId;

    @TableField("model_call_count")
    private Integer modelCallCount;

    @TableField("input_tokens")
    private Long inputTokens;

    @TableField("output_tokens")
    private Long outputTokens;

    @TableField("total_tokens")
    private Long totalTokens;

    @TableField("model_duration_ms")
    private Long modelDurationMs;

    @TableField("created_at")
    private Instant createdAt;
}