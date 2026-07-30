package com.leo.careerforgeai.model.exception;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-30 13:49
 **/
public enum ModelErrorType {

    CONFIGURATION_ERROR,
    AUTHENTICATION_ERROR,
    PERMISSION_ERROR,
    MODEL_NOT_FOUND,
    RATE_LIMITED,
    TIMEOUT,
    NETWORK_ERROR,
    PROVIDER_ERROR,
    INVALID_RESPONSE,
    STRUCTURED_OUTPUT_INVALID
}