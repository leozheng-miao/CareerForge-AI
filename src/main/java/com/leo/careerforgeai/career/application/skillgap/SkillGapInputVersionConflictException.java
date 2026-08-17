package com.leo.careerforgeai.career.application.skillgap;

/**
 * @program: CareerForge-AI
 * @description: 表示客户端提交的岗位或技能画像版本已经过期
 * @author: Miao Zheng
 * @date: 2026-08-17
 */
public class SkillGapInputVersionConflictException extends RuntimeException {
    public SkillGapInputVersionConflictException(String message) {
        super(message);
    }
}