package com.leo.careerforgeai.model.domain.toolcalling;

import com.leo.careerforgeai.model.domain.ModelUsage;

/** 定义模型返回最终回答或请求工具调用两种互斥结果。 */
public sealed interface ToolCallingModelResult permits FinalAnswerResult, ToolCallsResult {

    String requestId();
    String model();
    ModelUsage usage();
}