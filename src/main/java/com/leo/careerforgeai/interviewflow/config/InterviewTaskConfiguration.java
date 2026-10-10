package com.leo.careerforgeai.interviewflow.config;

import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.application.preparation.InterviewPreparationEngine;
import com.leo.careerforgeai.interviewflow.application.task.InterviewTaskHandlerRegistry.InterviewTaskHandler;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @program: CareerForge-AI
 * @description: 注册面试后台任务配置和准备任务处理器
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
@EnableScheduling
@EnableConfigurationProperties(InterviewTaskExecutorProperties.class)
public class InterviewTaskConfiguration {

    @Bean
    public InterviewTaskHandler prepareInterviewTaskHandler(
            InterviewPreparationEngine preparationEngine,
            InterviewSessionRepository sessions
    ) {
        return new InterviewTaskHandler() {
            @Override
            public TaskType taskType() {
                return TaskType.PREPARE_INTERVIEW;
            }

            @Override
            public void handle(
                    com.leo.careerforgeai.interviewflow.application.port
                            .InterviewTaskRepository.ClaimedTask task
            ) {
                var aggregate = sessions.findById(task.ownerId(), task.interviewId())
                        .orElseThrow(() -> new IllegalStateException("准备任务对应面试不存在"));
                if (aggregate.session().mode() == InterviewMode.COMPREHENSIVE) {
                    preparationEngine.prepareComprehensive(task.ownerId(), task.interviewId(),
                            task.inputVersion(), task.planVersion());
                } else {
                    preparationEngine.prepareQuick(task.ownerId(), task.interviewId(),
                            task.inputVersion(), task.planVersion());
                }
            }
        };
    }
}
