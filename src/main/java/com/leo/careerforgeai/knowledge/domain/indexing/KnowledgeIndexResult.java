package com.leo.careerforgeai.knowledge.domain.indexing;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 汇总一次 Bulk 请求的成功数量和逐项失败信息
 * @author: Miao Zheng
 * @date: 2026-08-03 13:54
 **/
public record KnowledgeIndexResult(
        int requestedCount,
        int indexedCount,
        List<KnowledgeIndexFailure> failures
) {

    public KnowledgeIndexResult {
        if (requestedCount < 0) throw new IllegalArgumentException("requestedCount 不能小于 0");
        if (indexedCount < 0) throw new IllegalArgumentException("indexedCount 不能小于 0");
        if (failures == null) throw new IllegalArgumentException("failures 不能为空");
        failures = List.copyOf(failures);
        if (indexedCount + failures.size() != requestedCount) throw new IllegalArgumentException("成功数与失败数之和必须等于请求数");
    }

    /** 返回本次 Bulk 中失败的 Chunk 数量。 */
    public int failedCount() {
        return failures.size();
    }

    /** 判断 Bulk 是否发生部分或全部失败。 */
    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}