package com.leo.careerforgeai.interview.api.dto.session;

import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;

import java.time.Instant;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回模拟面试身份、状态、用户可见预算和乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param interviewId 模拟面试UUID
 * @param inputSnapshotId 冻结输入快照UUID
 * @param mode 模拟面试模式
 * @param status 当前面试状态
 * @param maxQuestions 最大问题总数
 * @param maxFollowUps 最大追问数
 * @param version 面试乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 **/
public record MockInterviewSessionResponse(
        UUID interviewId,
        UUID inputSnapshotId,
        InterviewMode mode,
        InterviewStatus status,
        int maxQuestions,
        int maxFollowUps,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static MockInterviewSessionResponse from(MockInterviewSession session) {
        return new MockInterviewSessionResponse(
                session.interviewId(),
                session.inputSnapshotId(),
                session.mode(),
                session.status(),
                session.budgetPolicy().maxQuestions(),
                session.budgetPolicy().maxFollowUps(),
                session.version(),
                session.createdAt(),
                session.updatedAt()
        );
    }
}