package com.leo.careerforgeai.interviewflow.application.report;

import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.ClaimedTask;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.application.task.InterviewTaskHandlerRegistry.InterviewTaskHandler;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public final class GenerateReportTaskHandler implements InterviewTaskHandler {
    private final InterviewReportApplicationService reports;

    public GenerateReportTaskHandler(InterviewReportApplicationService reports) {
        this.reports = Objects.requireNonNull(reports);
    }

    @Override public TaskType taskType() { return TaskType.GENERATE_REPORT; }

    @Override public void handle(ClaimedTask task) {
        reports.generate(task.ownerId(), task.interviewId(), task.contextFingerprint());
    }

    @Override
    public void onTerminalFailure(ClaimedTask task, String errorCode) {
        reports.failTerminalTask(
                task.ownerId(),
                task.interviewId(),
                errorCode
        );
    }
}
