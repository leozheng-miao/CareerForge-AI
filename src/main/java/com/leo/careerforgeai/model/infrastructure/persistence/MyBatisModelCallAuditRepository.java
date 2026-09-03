package com.leo.careerforgeai.model.infrastructure.persistence;

import com.leo.careerforgeai.model.application.audit.ModelCallAuditRepository;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.audit.ModelCallAudit;
import com.leo.careerforgeai.model.infrastructure.persistence.entity.ModelCallAuditEntity;
import com.leo.careerforgeai.model.infrastructure.persistence.mapper.ModelCallAuditMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 将模型调用安全审计事实写入MySQL。
 * @author: Miao Zheng
 * @date: 2026-09-03
 */
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public final class MyBatisModelCallAuditRepository implements ModelCallAuditRepository {
    private final ModelCallAuditMapper mapper;

    public MyBatisModelCallAuditRepository(ModelCallAuditMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper不能为null");
    }

    @Override
    public void save(ModelCallAudit audit) {
        Objects.requireNonNull(audit, "audit不能为null");
        ModelCallAuditEntity entity = toEntity(audit);
        if (mapper.insert(entity) != 1) throw new IllegalStateException("模型调用审计写入失败");
    }

    private static ModelCallAuditEntity toEntity(ModelCallAudit audit) {
        ModelCallAuditEntity entity = new ModelCallAuditEntity();
        entity.setAuditId(audit.auditId().toString());
        entity.setStartedAt(audit.startedAt());
        entity.setTaskType(audit.taskType().name());
        entity.setOperationType(audit.operationType().name());
        entity.setProviderId(audit.providerId());
        entity.setModelProfile(audit.modelProfile());
        entity.setProviderModel(audit.providerModel());
        entity.setRoutingVersion(audit.routingVersion());
        entity.setPriceVersion(audit.priceVersion());
        entity.setReasoningMode(audit.reasoningMode().name());
        entity.setFallbackCall(audit.fallbackCall());
        entity.setOutcome(audit.outcome().name());
        entity.setErrorCategory(audit.errorCategory());
        entity.setProviderRequestId(audit.providerRequestId());
        ModelUsage usage = audit.usage();
        entity.setUsageStatus(usage == null ? "UNKNOWN" : "KNOWN");
        if (usage != null) {
            entity.setInputTokens(usage.inputTokens());
            entity.setOutputTokens(usage.outputTokens());
            entity.setTotalTokens(usage.totalTokens());
        }
        entity.setDurationMs(audit.durationMs());
        entity.setTraceId(audit.traceId());
        entity.setSpanId(audit.spanId());
        return entity;
    }
}
