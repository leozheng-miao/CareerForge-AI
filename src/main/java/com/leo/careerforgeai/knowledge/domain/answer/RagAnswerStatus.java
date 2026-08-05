package com.leo.careerforgeai.knowledge.domain.answer;

/**
 * @program: CareerForge-AI
 * @description: 区分已有知识库依据的回答和上下文不足时的固定拒答
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public enum RagAnswerStatus {
    ANSWERED,
    INSUFFICIENT_CONTEXT
}