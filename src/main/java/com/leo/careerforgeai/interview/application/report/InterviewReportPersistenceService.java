package com.leo.careerforgeai.interview.application.report;

import com.leo.careerforgeai.interview.application.port.InterviewNodeExecutionRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReportRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.session.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.execution.InterviewNodeExecution;
import com.leo.careerforgeai.interview.domain.execution.InterviewNodeExecutionStatus;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 认领报告节点并原子保存报告、模型收据和待确认Session状态
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Service
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        InterviewReportRepository.class,
        InterviewNodeExecutionRepository.class
})
public class InterviewReportPersistenceService {

    public static final String GENERATE_REPORT_NODE = "generate_report";

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewReportRepository reportRepository;
    private final InterviewNodeExecutionRepository executionRepository;
    private final Clock clock;

    public InterviewReportPersistenceService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewReportRepository reportRepository,
            InterviewNodeExecutionRepository executionRepository,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository不能为空");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public Claim claimGeneration(UUID interviewId, String inputHash) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(inputHash, "inputHash不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, interviewId);

        Optional<InterviewReport> existing = reportRepository.findByInterview(ownerId, interviewId);
        if (existing.isPresent()) {
            requireReportInput(existing.get(), ownerId, interviewId, inputHash);
            convergeAwaitingConfirmation(ownerId, session);
            return new Claim(existing.get(), null);
        }
        if (session.status() != InterviewStatus.GENERATING_REPORT) {
            throw new IllegalStateException("只有GENERATING_REPORT状态可以认领报告生成");
        }

        InterviewNodeExecution candidate = InterviewNodeExecution.start(
                UUID.randomUUID(),
                interviewId,
                ownerId,
                0,
                GENERATE_REPORT_NODE,
                inputHash,
                clock.instant()
        );
        InterviewNodeExecution stored = executionRepository.claim(candidate);

        if (stored.status() == InterviewNodeExecutionStatus.SUCCEEDED) {
            InterviewReport report = loadSuccessfulReport(stored);
            requireReportInput(report, ownerId, interviewId, inputHash);
            convergeAwaitingConfirmation(ownerId, session);
            return new Claim(report, null);
        }
        if (stored.status() == InterviewNodeExecutionStatus.RUNNING) {
            if (!stored.executionId().equals(candidate.executionId())) {
                throw new IllegalStateException("报告生成已经由另一个执行者处理");
            }
            return new Claim(null, stored);
        }

        InterviewNodeExecution retried = stored.retry(clock.instant());
        if (!executionRepository.updateIfVersionMatches(ownerId, retried, stored.version())) {
            throw new IllegalStateException("报告生成重试执行权CAS认领失败");
        }
        return new Claim(null, retried);
    }

    @Transactional
    public InterviewReport persist(
            InterviewReport candidate,
            InterviewNodeExecution execution,
            InterviewRoleModelGateway.Result<?> result
    ) {
        Objects.requireNonNull(candidate, "candidate不能为空");
        Objects.requireNonNull(execution, "execution不能为空");
        Objects.requireNonNull(result, "result不能为空");
        requireExecutionScope(candidate, execution);

        ActorId ownerId = currentActor();
        MockInterviewSession session = requireSession(ownerId, candidate.interviewId());
        if (session.status() != InterviewStatus.GENERATING_REPORT
                && session.status() != InterviewStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("当前Session状态不能保存报告");
        }

        InterviewReport stored = reportRepository.claim(candidate);
        requireStoredReport(candidate, stored);

        InterviewNodeExecution succeeded = execution.succeed(
                stored.reportId().toString(),
                result.requestId(),
                result.modelCallCount(),
                result.usage(),
                result.durationMs(),
                clock.instant()
        );
        if (!executionRepository.updateIfVersionMatches(ownerId, succeeded, execution.version())) {
            throw new IllegalStateException("报告节点成功状态CAS更新失败");
        }

        convergeAwaitingConfirmation(ownerId, session);
        return stored;
    }

    @Transactional
    public void fail(InterviewNodeExecution execution, InterviewFailureCode failureCode) {
        Objects.requireNonNull(execution, "execution不能为空");
        Objects.requireNonNull(failureCode, "failureCode不能为空");
        if (execution.status() != InterviewNodeExecutionStatus.RUNNING) return;

        InterviewNodeExecution failed = execution.failWithoutModel(failureCode.name(), clock.instant());
        if (!executionRepository.updateIfVersionMatches(
                execution.ownerId(), failed, execution.version()
        )) {
            throw new IllegalStateException("报告节点失败状态CAS更新失败");
        }
    }

    private InterviewReport loadSuccessfulReport(InterviewNodeExecution execution) {
        UUID reportId;
        try {
            reportId = UUID.fromString(execution.outputReferenceId());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("成功的报告节点缺少合法输出引用", exception);
        }

        return reportRepository.findById(
                execution.ownerId(),
                execution.interviewId(),
                reportId
        ).orElseThrow(() -> new IllegalStateException("报告节点成功但缺少报告事实"));
    }

    private void requireExecutionScope(
            InterviewReport report,
            InterviewNodeExecution execution
    ) {
        if (!execution.ownerId().equals(report.ownerId())
                || !execution.interviewId().equals(report.interviewId())
                || execution.roundNo() != 0
                || !execution.nodeName().equals(GENERATE_REPORT_NODE)
                || !execution.inputHash().equals(report.inputHash())
                || execution.status() != InterviewNodeExecutionStatus.RUNNING) {
            throw new IllegalStateException("报告与节点执行权作用域不一致");
        }
    }

    private void requireReportInput(
            InterviewReport report,
            ActorId ownerId,
            UUID interviewId,
            String inputHash
    ) {
        if (!report.ownerId().equals(ownerId)
                || !report.interviewId().equals(interviewId)
                || !report.inputHash().equals(inputHash)) {
            throw new IllegalStateException("已存在报告与当前输入身份不一致");
        }
    }

    private void requireStoredReport(InterviewReport candidate, InterviewReport stored) {
        if (!stored.reportId().equals(candidate.reportId())
                || !stored.ownerId().equals(candidate.ownerId())
                || !stored.interviewId().equals(candidate.interviewId())
                || !stored.inputHash().equals(candidate.inputHash())
                || !stored.outputHash().equals(candidate.outputHash())) {
            throw new IllegalStateException("报告幂等认领结果与当前候选不一致");
        }
    }

    private void convergeAwaitingConfirmation(ActorId ownerId, MockInterviewSession session) {
        if (session.status() == InterviewStatus.AWAITING_CONFIRMATION) return;
        if (session.status() != InterviewStatus.GENERATING_REPORT) {
            throw new IllegalStateException("已生成报告但Session状态无法收敛到AWAITING_CONFIRMATION");
        }

        MockInterviewSession updated = session.awaitConfirmation(clock.instant());
        if (!sessionRepository.updateIfVersionMatches(ownerId, updated, session.version())) {
            throw new MockInterviewVersionConflictException(session.interviewId(), session.version());
        }
    }

    private MockInterviewSession requireSession(ActorId ownerId, UUID interviewId) {
        return sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }

    /**
     * @program: CareerForge-AI
     * @description: 返回已有报告或本次获得的报告节点执行权
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param report 已存在并完成持久化的报告
     * @param execution 本次获得的报告节点执行权
     */
    public record Claim(InterviewReport report, InterviewNodeExecution execution) {

        public Claim {
            if ((report == null) == (execution == null)) {
                throw new IllegalArgumentException("report与execution必须且只能存在一个");
            }
        }

        public boolean completed() {
            return report != null;
        }
    }
}