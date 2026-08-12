package com.leo.careerforgeai.memory.domain.conversation;

/**
 * @program: CareerForge-AI
 * @description: 定义Conversation Turn在会话中的消息角色
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public enum ConversationTurnRole {

    /** 用户提交的原始问题或补充信息。 */
    USER,

    /** 经过Java最终回答校验后保存的Career Coach回答。 */
    ASSISTANT
}