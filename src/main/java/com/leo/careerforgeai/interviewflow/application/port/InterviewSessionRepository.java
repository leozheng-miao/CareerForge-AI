package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义新面试会话的幂等创建、owner查询和CAS更新端口
 * @author: Miao Zheng
 * @date: 2026-10-02
 */
public interface InterviewSessionRepository {

    InterviewAggregate claim(SessionClaim claim);

    Optional<InterviewAggregate> findById(
            ActorId ownerId,
            UUID interviewId
    );

    Optional<InterviewAggregate> findByRequestId(
            ActorId ownerId,
            UUID requestId
    );

    Optional<InterviewSession> findInProgress(ActorId ownerId);

    default Optional<SessionVersions> findVersions(
            ActorId ownerId,
            UUID interviewId
    ) {
        return findById(ownerId, interviewId)
                .map(ignored -> new SessionVersions(1, 1));
    }

    boolean updateIfVersionMatches(
            ActorId ownerId,
            InterviewAggregate updated,
            long expectedVersion
    );

    default List<InterviewAggregate> findRunningStartedBefore(
            Instant cutoff,
            int limit
    ) {
        return List.of();
    }

    /** 按创建时间倒序返回owner的面试记录。 */
    default List<InterviewAggregate> findPage(
            ActorId ownerId,
            Instant beforeCreatedAt,
            UUID beforeInterviewId,
            int limit
    ) {
        return List.of();
    }

    /**
     * @param requestId 创建请求幂等ID
     * @param requestFingerprint 影响创建语义的请求指纹
     * @param candidate 待创建的面试聚合
     */
    record SessionClaim(
            UUID requestId,
            String requestFingerprint,
            InterviewAggregate candidate
    ) {

        public SessionClaim {
            Objects.requireNonNull(requestId, "requestId不能为空");
            Objects.requireNonNull(candidate, "candidate不能为空");
            if (requestFingerprint == null
                    || requestFingerprint.length() != 64) {
                throw new IllegalArgumentException(
                        "requestFingerprint必须为64位"
                );
            }
        }
    }

    record SessionVersions(long inputVersion, long planVersion) {

        public SessionVersions {
            if (inputVersion < 1 || planVersion < 1) {
                throw new IllegalArgumentException(
                        "会话输入和计划版本必须大于等于1"
                );
            }
        }
    }
}
