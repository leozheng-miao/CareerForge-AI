package com.leo.careerforgeai.interviewflow.application.report;

import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewEventPublisher;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository.ReportSnapshot;
import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository.ReportStatus;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.ClaimedTask;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTopicRepository;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewReportApplicationServiceTest {
    private static final ActorId OWNER = new ActorId("report-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString(
            "52000000-0000-0000-0000-000000000001"
    );
    private static final Instant CREATED_AT = Instant.parse(
            "2026-10-08T04:35:00Z"
    );
    private static final Instant NOW = Instant.parse(
            "2026-10-08T06:28:20Z"
    );

    @Test
    void shouldFailGeneratingReportWhenTaskExhaustsRetries() {
        InMemoryReportRepository reports = new InMemoryReportRepository(
                new ReportSnapshot(
                        UUID.fromString(
                                "52000000-0000-0000-0000-000000000002"
                        ),
                        INTERVIEW_ID,
                        OWNER,
                        ReportStatus.GENERATING,
                        null,
                        1,
                        1,
                        1,
                        "a".repeat(64),
                        null,
                        41,
                        CREATED_AT,
                        CREATED_AT.plusSeconds(30),
                        null
                )
        );
        GenerateReportTaskHandler handler = new GenerateReportTaskHandler(
                service(reports)
        );
        ClaimedTask task = new ClaimedTask(
                UUID.fromString("52000000-0000-0000-0000-000000000003"),
                INTERVIEW_ID,
                OWNER,
                TaskType.GENERATE_REPORT,
                "report:terminal-failure",
                3,
                3,
                1,
                1,
                "a".repeat(64),
                3,
                "report-worker",
                NOW.plusSeconds(90)
        );

        handler.onTerminalFailure(task, "TASK_TIMEOUT");
        handler.onTerminalFailure(task, "TASK_TIMEOUT");

        assertThat(reports.current.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(reports.current.failureCode()).isEqualTo(
                "TASK_TIMEOUT"
        );
        assertThat(reports.current.version()).isEqualTo(42);
        assertThat(reports.current.completedAt()).isEqualTo(NOW);
    }

    private static InterviewReportApplicationService service(
            InterviewReportRepository reports
    ) {
        JsonMapper json = JsonMapper.builder().build();
        return new InterviewReportApplicationService(
                unused(CurrentActorProvider.class),
                unused(InterviewSessionRepository.class),
                unused(InterviewTopicRepository.class),
                unused(InterviewQuestionRepository.class),
                unused(InterviewAnswerRepository.class),
                reports,
                unused(InterviewTaskRepository.class),
                new InterviewReportEngine(
                        unused(ModelGateway.class),
                        json
                ),
                unused(InterviewEventPublisher.class),
                json,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static <T> T unused(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    throw new AssertionError(
                            "本测试不应调用 " + type.getSimpleName()
                                    + "." + method.getName()
                    );
                }
        ));
    }

    private static final class InMemoryReportRepository
            implements InterviewReportRepository {
        private ReportSnapshot current;

        private InMemoryReportRepository(ReportSnapshot current) {
            this.current = current;
        }

        @Override
        public void insert(ReportSnapshot report) {
            throw new AssertionError("不应新建报告");
        }

        @Override
        public Optional<ReportSnapshot> findByInterview(
                ActorId ownerId,
                UUID interviewId
        ) {
            return current.ownerId().equals(ownerId)
                    && current.interviewId().equals(interviewId)
                    ? Optional.of(current)
                    : Optional.empty();
        }

        @Override
        public boolean updateIfVersionMatches(
                ActorId ownerId,
                ReportSnapshot updated,
                long expectedVersion
        ) {
            if (!current.ownerId().equals(ownerId)
                    || current.version() != expectedVersion) {
                return false;
            }
            current = updated;
            return true;
        }
    }
}
