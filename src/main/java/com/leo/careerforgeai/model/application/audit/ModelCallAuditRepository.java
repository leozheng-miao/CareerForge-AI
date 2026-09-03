package com.leo.careerforgeai.model.application.audit;

import com.leo.careerforgeai.model.domain.audit.ModelCallAudit;

/** 定义模型调用安全审计的耐久写入端口。 */
@FunctionalInterface
public interface ModelCallAuditRepository {
    void save(ModelCallAudit audit);
}
