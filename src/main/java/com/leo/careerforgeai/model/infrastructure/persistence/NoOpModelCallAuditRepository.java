package com.leo.careerforgeai.model.infrastructure.persistence;

import com.leo.careerforgeai.model.application.audit.ModelCallAuditRepository;
import com.leo.careerforgeai.model.domain.audit.ModelCallAudit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @program: CareerForge-AI
 * @description: 在关闭MySQL持久化的普通测试中接收但不写入模型审计。
 * @author: Miao Zheng
 * @date: 2026-09-03
 */
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "false")
public final class NoOpModelCallAuditRepository implements ModelCallAuditRepository {
    @Override
    public void save(ModelCallAudit audit) {
        // 普通测试明确关闭持久化，不连接真实MySQL。
    }
}
