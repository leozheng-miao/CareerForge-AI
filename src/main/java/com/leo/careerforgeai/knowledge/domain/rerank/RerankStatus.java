package com.leo.careerforgeai.knowledge.domain.rerank;

/**
 * @program: CareerForge-AI
 * @description: 标识本次 Rerank 是成功、关闭、因空候选跳过还是失败回退
 * APPLIED       → 使用 LLM 新顺序
 * DISABLED      → 主动关闭，使用 RRF
 * SKIPPED_EMPTY → 没有候选，不调用模型
 * FALLBACK      → LLM 失败，使用 RRF
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public enum RerankStatus {
    APPLIED,
    DISABLED,
    SKIPPED_EMPTY,
    FALLBACK
}