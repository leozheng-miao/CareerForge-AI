package com.leo.careerforgeai.memory.application.extraction.dto;

/**
 * @program: CareerForge-AI
 * @description: 定义Memory提取输入、模型调用和模型输出的稳定失败分类
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
public enum MemoryExtractionErrorType {

    SOURCE_INPUT_INVALID,
    INPUT_SERIALIZATION_FAILED,
    MODEL_CALL_FAILED,
    MODEL_OUTPUT_INVALID
}