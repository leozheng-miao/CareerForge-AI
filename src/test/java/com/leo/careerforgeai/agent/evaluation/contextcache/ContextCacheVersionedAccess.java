package com.leo.careerforgeai.agent.evaluation.contextcache;

/**
 * @program: CareerForge-AI
 * @description: 保存一次Context缓存候选的版本化读取事实
 * @author: Miao Zheng
 * @date: 2026-08-24
 * @param candidate 缓存候选类型
 * @param sequence 从1开始的访问顺序
 * @param ownerKey 已脱敏的owner缓存身份
 * @param resourceKey Session ID或Profile固定资源标识
 * @param version 本次读取对应的业务版本
 * @param versionCoversPayload 当前版本是否覆盖缓存载荷中的全部业务变化
 */
public record ContextCacheVersionedAccess(
        ContextCacheCandidate candidate,
        int sequence,
        String ownerKey,
        String resourceKey,
        long version,
        boolean versionCoversPayload
) {

    public ContextCacheVersionedAccess {
        if (candidate == null) throw new IllegalArgumentException("candidate不能为空");
        if (sequence <= 0) throw new IllegalArgumentException("sequence必须大于0");
        if (ownerKey == null || ownerKey.isBlank()) throw new IllegalArgumentException("ownerKey不能为空");
        if (resourceKey == null || resourceKey.isBlank()) throw new IllegalArgumentException("resourceKey不能为空");
        if (version < 0) throw new IllegalArgumentException("version不能小于0");
    }

    public String versionedIdentity() {
        return ownerKey + "\u0000" + resourceKey + "\u0000" + version;
    }

    public String resourceIdentity() {
        return ownerKey + "\u0000" + resourceKey;
    }
}