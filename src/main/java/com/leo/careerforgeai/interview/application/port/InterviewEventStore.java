package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.application.event.InterviewEvent;
import com.leo.careerforgeai.interview.application.event.StoredInterviewEvent;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义短期模拟面试安全事件的Redis追加和有界续读边界
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
public interface InterviewEventStore {

    String append(InterviewEvent event);

    List<StoredInterviewEvent> readAfter(
            ActorId ownerId,
            UUID interviewId,
            String lastEventId,
            int limit
    );
}