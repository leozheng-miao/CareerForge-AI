package com.leo.careerforgeai.memory.domain.conversation;

/**
 * @program: CareerForge-AI
 * @description: 定义Conversation Turn是否形成了可用的正常对话内容
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public enum ConversationTurnStatus {

    /** 消息已经完成，可以参与短期Context或显式Memory提取。 */
    COMPLETED,

    /** 助手运行失败，只保存错误分类，不保存未经校验的模型输出。 */
    FAILED
}