package com.leo.careerforgeai.memory.infrastructure.memory;

import com.leo.careerforgeai.interview.application.port.InterviewMemorySuggestionApplicationPort;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 将用户确认的结构化面试报告建议转换为可追溯且可幂等重放的PENDING Memory候选
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Component
@ConditionalOnBean(MemoryRepository.class)
public class MemoryInterviewSuggestionAdapter implements InterviewMemorySuggestionApplicationPort {

    private static final String ID_NAMESPACE = "interview-report-memory-v1";

    private final MemoryRepository memoryRepository;
    private final Clock clock;

    public MemoryInterviewSuggestionAdapter(MemoryRepository memoryRepository, Clock clock) {
        this.memoryRepository = Objects.requireNonNull(memoryRepository, "memoryRepository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Override
    public UUID apply(
            InterviewReport report,
            InterviewReportConfirmation.Decision decision
    ) {
        Objects.requireNonNull(report, "report不能为空");
        Objects.requireNonNull(decision, "decision不能为空");
        requireApplicableDecision(report, decision);

        InterviewReport.Suggestion suggestion = report.suggestions().stream()
                .filter(candidate -> candidate.suggestionId().equals(decision.suggestionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("确认决定引用的报告建议不存在"));

        if (suggestion.type() != InterviewReport.SuggestionType.MEMORY_CANDIDATE) {
            throw new IllegalArgumentException("训练计划建议不能通过Memory应用端口处理");
        }
        if (!(suggestion.payload() instanceof InterviewReport.MemoryCandidatePayload payload)) {
            if (suggestion.payload() instanceof InterviewReport.LegacyPayload) {
                throw new IllegalStateException("旧版字符串Memory建议不可执行，请重新生成报告");
            }
            throw new IllegalStateException("Memory建议payload类型不合法");
        }

        UUID memoryId = stableMemoryId(report, suggestion);
        MemoryItem candidate = MemoryItem.createPending(
                memoryId,
                report.ownerId(),
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence(payload.skillName()),
                payload.content(),
                new MemorySource(
                        MemorySourceType.INTERVIEW_REPORT,
                        suggestion.suggestionId().toString(),
                        suggestion.contentHash()
                ),
                evidenceRefs(report, suggestion),
                clock.instant()
        );

        return memoryRepository.findById(report.ownerId(), memoryId)
                .map(existing -> requireReplay(existing, candidate))
                .orElseGet(() -> insertOrReplay(candidate));
    }

    private UUID insertOrReplay(MemoryItem candidate) {
        try {
            memoryRepository.insert(candidate);
            return candidate.memoryId();
        } catch (DataIntegrityViolationException exception) {
            return memoryRepository.findById(candidate.ownerId(), candidate.memoryId())
                    .map(existing -> requireReplay(existing, candidate))
                    .orElseThrow(() -> new IllegalStateException(
                            "Memory候选唯一约束冲突，但未找到可重放记录", exception
                    ));
        }
    }

    private UUID requireReplay(MemoryItem existing, MemoryItem expected) {
        if (!existing.memoryId().equals(expected.memoryId())
                || !existing.ownerId().equals(expected.ownerId())
                || existing.type() != expected.type()
                || !existing.normalizedKey().equals(expected.normalizedKey())
                || !existing.content().equals(expected.content())
                || !existing.contentHash().equals(expected.contentHash())
                || !existing.source().equals(expected.source())
                || !existing.evidenceRefs().equals(expected.evidenceRefs())
                || existing.supersedesId() != null) {
            throw new IllegalStateException("稳定Memory ID已被不同业务内容占用");
        }
        return existing.memoryId();
    }

    private void requireApplicableDecision(
            InterviewReport report,
            InterviewReportConfirmation.Decision decision
    ) {
        if (!decision.reportId().equals(report.reportId())
                || !decision.interviewId().equals(report.interviewId())
                || !decision.ownerId().equals(report.ownerId())) {
            throw new IllegalArgumentException("确认决定与报告作用域不一致");
        }
        if (decision.decisionType() != InterviewReportConfirmation.DecisionType.CONFIRMED
                || decision.applicationStatus() != InterviewReportConfirmation.ApplicationStatus.PENDING) {
            throw new IllegalStateException("只有用户已确认且等待应用的建议可以写入Memory候选区");
        }
    }

    private UUID stableMemoryId(
            InterviewReport report,
            InterviewReport.Suggestion suggestion
    ) {
        String identity = ID_NAMESPACE + '\0'
                + report.ownerId().value() + '\0'
                + report.reportId() + '\0'
                + suggestion.suggestionId();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> evidenceRefs(
            InterviewReport report,
            InterviewReport.Suggestion suggestion
    ) {
        return List.of(
                "interview:" + report.interviewId(),
                "interview-report:" + report.reportId(),
                "report-suggestion:" + suggestion.suggestionId()
        );
    }
}