package com.leo.careerforgeai.agent.evaluation.dataset;

/**
 * @program: CareerForge-AI
 * @description: 表示固定Agent评测资源缺失、解析失败或标注不合法。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public class AgentEvaluationDatasetException extends IllegalStateException {

    public AgentEvaluationDatasetException(String message) {
        super(message);
    }

    public AgentEvaluationDatasetException(String message, Throwable cause) {
        super(message, cause);
    }
}