package com.leo.careerforgeai.model.application;

import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;

/** 定义供应商无关的非流式 Tool Calling 模型能力。 */
public interface ToolCallingGateway {

    ToolCallingModelResult call(ToolCallingRequest request);
}