package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.interviewflow.domain.topic.InterviewTopic;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义面试考查目标的有序保存、owner查询和CAS更新端口
 * @author: Miao Zheng
 * @date: 2026-10-02
 */
public interface InterviewTopicRepository {

    void insertAll(
            ActorId ownerId,
            UUID interviewId,
            long planVersion,
            List<InterviewTopic> topics
    );

    Optional<InterviewTopic> findTopic(
            ActorId ownerId,
            UUID interviewId,
            UUID topicId
    );

    default Optional<TopicRecord> findTopicRecord(
            ActorId ownerId,
            UUID interviewId,
            UUID topicId
    ) {
        return findTopic(ownerId, interviewId, topicId)
                .map(topic -> new TopicRecord(topic, 0));
    }

    List<InterviewTopic> findTopics(
            ActorId ownerId,
            UUID interviewId
    );

    boolean updateIfVersionMatches(
            ActorId ownerId,
            UUID interviewId,
            InterviewTopic updated,
            long expectedVersion
    );

    record TopicRecord(InterviewTopic topic, long version) {

        public TopicRecord {
            if (topic == null) {
                throw new IllegalArgumentException("topic不能为空");
            }
            if (version < 0) {
                throw new IllegalArgumentException("version不能小于0");
            }
        }
    }
}
