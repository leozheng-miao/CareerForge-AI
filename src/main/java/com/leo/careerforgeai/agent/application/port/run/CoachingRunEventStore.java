package com.leo.careerforgeai.agent.application.port.run;

import com.leo.careerforgeai.agent.application.run.event.CoachingRunEvent;
import com.leo.careerforgeai.agent.application.run.event.StoredCoachingRunEvent;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义短期Run观察事件的追加和有界读取端口
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
public interface CoachingRunEventStore {

    String append(CoachingRunEvent event);

    List<StoredCoachingRunEvent> readAfter(
            ActorId ownerId,
            UUID runId,
            String lastEventId,
            int limit
    );
}