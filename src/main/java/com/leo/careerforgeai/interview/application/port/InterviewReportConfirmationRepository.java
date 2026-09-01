package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义报告确认单及逐项决定的原子认领、owner隔离查询和乐观锁更新边界
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
public interface InterviewReportConfirmationRepository {

    InterviewReportConfirmation claim(InterviewReportConfirmation candidate);

    Optional<InterviewReportConfirmation> findByRequest(ActorId ownerId, UUID requestId);

    Optional<InterviewReportConfirmation> findByReport(
            ActorId ownerId,
            UUID interviewId,
            UUID reportId
    );

    boolean updateIfVersionMatches(
            ActorId ownerId,
            InterviewReportConfirmation updatedConfirmation,
            long expectedVersion
    );
}