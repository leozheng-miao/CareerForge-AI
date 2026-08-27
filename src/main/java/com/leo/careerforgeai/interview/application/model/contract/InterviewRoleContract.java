package com.leo.careerforgeai.interview.application.model.contract;

import com.leo.careerforgeai.interview.domain.InterviewRole;

/**
 * @program: CareerForge-AI
 * @description: 统一定义面试模型角色的输入校验、输出Schema和输出可信化契约
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param <I> 角色输入类型
 * @param <O> 角色结构化输出类型
 **/
public interface InterviewRoleContract<I, O> {

    InterviewRole role();

    Class<O> outputType();

    String outputJsonSchema();

    void validateInput(I input);

    void validateOutputStructure(O output);

    O validateOutput(I input, O output);
}