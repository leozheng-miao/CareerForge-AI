package com.leo.careerforgeai.agent.domain.tool.career;

/**
 * @program: CareerForge-AI
 * @description: 区分职业材料搜索成功、无证据、系统错误和超时结果。
 * @author: Miao Zheng
 * @date: 2026-08-06 21:00
 **/
public enum SearchCareerMaterialsStatus {
    SUCCESS,
    NO_EVIDENCE,
    SYSTEM_ERROR,
    TIMEOUT
}