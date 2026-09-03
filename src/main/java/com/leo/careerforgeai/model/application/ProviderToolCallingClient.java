package com.leo.careerforgeai.model.application;

import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;

/**
 * @program: CareerForge-AI
 * @description: 定义统一Tool Calling协议与供应商协议之间的适配端口。
 * @author: Miao Zheng
 * @date: 2026-09-03
 */
public interface ProviderToolCallingClient {
    String providerId();
    ToolCallingModelResult call(ModelExecutionProfile profile, ToolCallingRequest request);
}