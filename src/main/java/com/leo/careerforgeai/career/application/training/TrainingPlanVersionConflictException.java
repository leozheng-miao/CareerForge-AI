package com.leo.careerforgeai.career.application.training;

/**
 * @program: CareerForge-AI
 * @description: 表示训练计划确认或进度更新使用了过期版本或发生并发冲突
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
public final class TrainingPlanVersionConflictException extends IllegalStateException {

    public TrainingPlanVersionConflictException(String message) {
        super(message);
    }
}