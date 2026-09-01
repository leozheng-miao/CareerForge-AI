package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义报告聚合及其有序建议的原子持久化和owner隔离查询边界
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
public interface InterviewReportRepository {

    InterviewReport claim(InterviewReport candidate);

    Optional<InterviewReport> findByInterview(ActorId ownerId, UUID interviewId);

    Optional<InterviewReport> findById(ActorId ownerId, UUID interviewId, UUID reportId);

    boolean updateIfVersionMatches(ActorId ownerId, InterviewReport updatedReport, long expectedVersion);
}