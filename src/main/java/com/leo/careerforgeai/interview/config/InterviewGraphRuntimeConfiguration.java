package com.leo.careerforgeai.interview.config;

import com.leo.careerforgeai.interview.application.answer.InterviewAnswerSubmissionService;
import com.leo.careerforgeai.interview.application.graph.InterviewGraphExecutionService;
import com.leo.careerforgeai.interview.application.graph.InterviewGraphState;
import com.leo.careerforgeai.interview.application.graph.InterviewGraphWorkflow;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.checkpoint.CreateOption;
import org.bsc.langgraph4j.checkpoint.MysqlSaver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @program: CareerForge-AI
 * @description: 装配正式面试Graph、MySQL Checkpoint Saver和并行评审虚拟线程执行器
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "careerforge.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class InterviewGraphRuntimeConfiguration {

    @Bean
    public MysqlSaver interviewCheckpointSaver(DataSource dataSource) {
        return MysqlSaver.builder()
                .dataSource(dataSource)
                .createOption(CreateOption.CREATE_NONE)
                .build();
    }

    @Bean(name = "interviewReviewExecutor", destroyMethod = "close")
    public ExecutorService interviewReviewExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("careerforge-interview-review-", 0).factory()
        );
    }

    @Bean
    public CompiledGraph<InterviewGraphState> interviewCompiledGraph(
            InterviewGraphWorkflow workflow, MysqlSaver interviewCheckpointSaver
    ) throws GraphStateException {
        return workflow.compile(interviewCheckpointSaver);
    }

    @Bean
    public InterviewGraphExecutionService interviewGraphExecutionService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewAnswerSubmissionService answerSubmissionService,
            CompiledGraph<InterviewGraphState> interviewCompiledGraph,
            @Qualifier("interviewReviewExecutor") Executor reviewExecutor
    ) {
        return new InterviewGraphExecutionService(
                currentActorProvider, sessionRepository, answerSubmissionService, interviewCompiledGraph, reviewExecutor
        );
    }
}