package com.leo.careerforgeai.agent.domain.coach;

/**
 * @program: CareerForge-AI
 * @description: 区分Career Coach正常回答、证据不足和安全拒答。
 * INSUFFICIENT_EVIDENCE：工具正常执行，但返回 NO_EVIDENCE。
 * UNAVAILABLE：必要工具发生 SYSTEM_ERROR 或 TIMEOUT。
 * REFUSED：请求违反安全边界。
 * ANSWERED：正常回答，可以有合法引用，也可以没有引用。
 * @author: Miao Zheng
 * @date: 2026-08-07 02:40
 **/
public enum CareerCoachAnswerStatus {
    ANSWERED,
    INSUFFICIENT_EVIDENCE,
    REFUSED,
    UNAVAILABLE
}