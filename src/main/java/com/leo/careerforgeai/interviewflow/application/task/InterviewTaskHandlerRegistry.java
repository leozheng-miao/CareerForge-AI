package com.leo.careerforgeai.interviewflow.application.task;

import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.ClaimedTask;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 将受限后台任务类型映射到唯一业务处理器
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Component
public final class InterviewTaskHandlerRegistry {

    private final Map<TaskType, InterviewTaskHandler> handlers;

    public InterviewTaskHandlerRegistry(List<InterviewTaskHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers不能为空");
        EnumMap<TaskType, InterviewTaskHandler> mapped =
                new EnumMap<>(TaskType.class);
        for (InterviewTaskHandler handler : handlers) {
            Objects.requireNonNull(handler, "handler不能为空");
            if (mapped.putIfAbsent(handler.taskType(), handler) != null) {
                throw new IllegalStateException(
                        "同一面试任务类型只能注册一个处理器: "
                                + handler.taskType()
                );
            }
        }
        this.handlers = Map.copyOf(mapped);
    }

    public InterviewTaskHandler require(TaskType taskType) {
        InterviewTaskHandler handler = handlers.get(
                Objects.requireNonNull(taskType, "taskType不能为空")
        );
        if (handler == null) {
            throw new IllegalStateException(
                    "未注册面试任务处理器: " + taskType
            );
        }
        return handler;
    }

    public interface InterviewTaskHandler {

        TaskType taskType();

        void handle(ClaimedTask task);

        default void onTerminalFailure(
                ClaimedTask task,
                String errorCode
        ) {
        }
    }
}
