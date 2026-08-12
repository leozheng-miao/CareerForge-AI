package com.leo.careerforgeai.memory.domain.conversation;

/**
 * @program: CareerForge-AI
 * @description: 定义求职辅导会话是否仍允许继续写入对话消息
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public enum CoachingSessionStatus {

    /** 会话正在使用，允许继续提交用户消息和保存助手回答。 */
    ACTIVE,

    /** 会话已经由用户关闭，只允许读取历史，不允许继续写入。 */
    CLOSED
}