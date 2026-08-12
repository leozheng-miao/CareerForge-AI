package com.leo.careerforgeai.memory.domain;

import java.util.Arrays;

/**
 * @program: CareerForge-AI
 * @description: 定义求职训练时间约束的固定分类槽位
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public enum TimeConstraintKey {

    WEEKLY_HOURS("weekly_hours"),
    TARGET_DEADLINE("target_deadline"),
    AVAILABLE_SCHEDULE("available_schedule");

    private final String value;

    TimeConstraintKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static boolean supports(String value) {
        return Arrays.stream(values())
                .anyMatch(key -> key.value.equals(value));
    }
}