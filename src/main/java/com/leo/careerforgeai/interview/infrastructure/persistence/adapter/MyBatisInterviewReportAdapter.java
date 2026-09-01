package com.leo.careerforgeai.interview.infrastructure.persistence.adapter;

import com.leo.careerforgeai.interview.application.port.InterviewReportRepository;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.InterviewReportPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportPersistenceModels.ReportRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportPersistenceModels.SuggestionRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.InterviewReportFactMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 原子保存报告和建议并实现owner隔离、幂等认领及乐观锁更新
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MyBatisInterviewReportAdapter implements InterviewReportRepository {

    private final InterviewReportFactMapper mapper;
    private final InterviewReportPersistenceConverter converter;

    public MyBatisInterviewReportAdapter(
            InterviewReportFactMapper mapper,
            InterviewReportPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper不能为空");
        this.converter = Objects.requireNonNull(converter, "converter不能为空");
    }

    @Override
    @Transactional
    public InterviewReport claim(InterviewReport candidate) {
        Objects.requireNonNull(candidate, "candidate不能为空");
        mapper.claimReport(converter.toReportRow(candidate));

        ReportRow stored = mapper.findByInterview(candidate.ownerId().value(), candidate.interviewId().toString());
        if (stored == null) throw new IllegalStateException("报告认领后无法读取");

        if (stored.getReportId().equals(candidate.reportId().toString())) {
            List<SuggestionRow> suggestions = converter.toSuggestionRows(candidate);
            if (!suggestions.isEmpty()) mapper.claimSuggestions(suggestions);
        }

        return load(stored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewReport> findByInterview(ActorId ownerId, UUID interviewId) {
        requireScope(ownerId, interviewId);
        ReportRow report = mapper.findByInterview(ownerId.value(), interviewId.toString());
        return report == null ? Optional.empty() : Optional.of(load(report));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewReport> findById(ActorId ownerId, UUID interviewId, UUID reportId) {
        requireScope(ownerId, interviewId);
        Objects.requireNonNull(reportId, "reportId不能为空");
        ReportRow report = mapper.findById(ownerId.value(), interviewId.toString(), reportId.toString());
        return report == null ? Optional.empty() : Optional.of(load(report));
    }

    @Override
    @Transactional
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            InterviewReport updatedReport,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updatedReport, "updatedReport不能为空");
        if (!updatedReport.ownerId().equals(ownerId)) {
            throw new IllegalArgumentException("updatedReport不属于当前owner");
        }
        if (expectedVersion < 0 || updatedReport.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("报告乐观锁版本不连续");
        }
        return mapper.updateIfVersionMatches(converter.toReportRow(updatedReport), expectedVersion) == 1;
    }

    private InterviewReport load(ReportRow report) {
        List<SuggestionRow> suggestions = mapper.findSuggestions(
                report.getOwnerId(), report.getInterviewId(), report.getReportId()
        );
        return converter.toDomain(report, suggestions);
    }

    private static void requireScope(ActorId ownerId, UUID interviewId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
    }
}