package com.leo.careerforgeai.model.exception;

/**
 * @program: CareerForge-AI
 * @description: 定义模型调用的稳定错误分类
 * @author: Miao Zheng
 * @date: 2026-08-24
 **/
public enum ModelErrorType {

    CONFIGURATION_ERROR,
    AUTHENTICATION_ERROR,
    PERMISSION_ERROR,
    MODEL_NOT_FOUND,
    RATE_LIMITED,
    CAPACITY_REJECTED,
    CIRCUIT_OPEN,
    TIMEOUT,
    NETWORK_ERROR,
    PROVIDER_ERROR,
    PROVIDER_REQUEST_REJECTED,
    INVALID_RESPONSE,
    STRUCTURED_OUTPUT_INVALID
}