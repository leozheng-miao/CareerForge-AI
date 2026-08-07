package com.leo.careerforgeai.agent.domain.tool.career;

/**
 * @program: CareerForge-AI
 * @description: 区分岗位解析成功、系统失败和上游超时。
 * @author: Miao Zheng
 * @date: 2026-08-07 01:10
 **/
public enum ParseJobRequirementsStatus {
    SUCCESS,
    SYSTEM_ERROR,
    TIMEOUT
}