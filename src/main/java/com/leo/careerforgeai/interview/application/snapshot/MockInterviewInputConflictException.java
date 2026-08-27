package com.leo.careerforgeai.interview.application.snapshot;

/**
 * @program: CareerForge-AI
 * @description: 表示创建面试快照时输入不存在、已撤销或版本已经变化
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public final class MockInterviewInputConflictException extends RuntimeException {

    public MockInterviewInputConflictException() {
        super("模拟面试输入不存在、已撤销或版本已经变化");
    }
}