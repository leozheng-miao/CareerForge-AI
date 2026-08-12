package com.leo.careerforgeai.memory.domain.profile;

import java.util.Arrays;

/**
 * @program: CareerForge-AI
 * @description: 定义学习偏好的固定分类槽位，避免模型自由创建不可控的偏好类别
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public enum LearningPreferenceKey {

    CONTENT_FORMAT("content_format"),
    FEEDBACK_STYLE("feedback_style"),
    LEARNING_PACE("learning_pace");

    private final String value;

    LearningPreferenceKey(String value) {
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