package com.leo.careerforgeai.interview.infrastructure.persistence.adapter;

import com.leo.careerforgeai.interview.application.port.InterviewReportConfirmationRepository;
import com.leo.careerforgeai.interview.domain.InterviewReportConfirmation;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.InterviewReportConfirmationPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportConfirmationPersistenceModels.ConfirmationRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportConfirmationPersistenceModels.DecisionRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.InterviewReportConfirmationFactMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @program: CareerForge-AI
 * @description: 原子保存确认单和决定并实现幂等认领、owner隔离及聚合乐观锁更新
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MyBatisInterviewReportConfirmationAdapter
        implements InterviewReportConfirmationRepository {

    private final InterviewReportConfirmationFactMapper mapper;
    private final InterviewReportConfirmationPersistenceConverter converter;

    public MyBatisInterviewReportConfirmationAdapter(
            InterviewReportConfirmationFactMapper mapper,
            InterviewReportConfirmationPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper不能为空");
        this.converter = Objects.requireNonNull(converter, "converter不能为空");
    }

    @Override
    @Transactional
    public InterviewReportConfirmation claim(InterviewReportConfirmation candidate) {
        Objects.requireNonNull(candidate, "candidate不能为空");
        mapper.claimConfirmation(converter.toConfirmationRow(candidate));

        ConfirmationRow stored = mapper.findByRequest(
                candidate.ownerId().value(), candidate.requestId().toString()
        );
        if (stored == null) {
            stored = mapper.findByReport(
                    candidate.ownerId().value(),
                    candidate.interviewId().toString(),
                    candidate.reportId().toString()
            );
        }
        if (stored == null) throw new IllegalStateException("确认单认领后无法读取");

        if (stored.getConfirmationId().equals(candidate.confirmationId().toString())) {
            List<DecisionRow> decisions = converter.toDecisionRows(candidate);
            if (!decisions.isEmpty()) mapper.claimDecisions(decisions);
        }
        return load(stored);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewReportConfirmation> findByRequest(ActorId ownerId, UUID requestId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");
        ConfirmationRow row = mapper.findByRequest(ownerId.value(), requestId.toString());
        return row == null ? Optional.empty() : Optional.of(load(row));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewReportConfirmation> findByReport(
            ActorId ownerId,
            UUID interviewId,
            UUID reportId
    ) {
        requireScope(ownerId, interviewId, reportId);
        ConfirmationRow row = mapper.findByReport(
                ownerId.value(), interviewId.toString(), reportId.toString()
        );
        return row == null ? Optional.empty() : Optional.of(load(row));
    }

    @Override
    @Transactional
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            InterviewReportConfirmation updatedConfirmation,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updatedConfirmation, "updatedConfirmation不能为空");
        if (!updatedConfirmation.ownerId().equals(ownerId)) {
            throw new IllegalArgumentException("updatedConfirmation不属于当前owner");
        }
        if (expectedVersion < 0 || updatedConfirmation.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("确认单乐观锁版本不连续");
        }

        ConfirmationRow currentRow = mapper.findByReport(
                ownerId.value(),
                updatedConfirmation.interviewId().toString(),
                updatedConfirmation.reportId().toString()
        );
        if (currentRow == null
                || !currentRow.getConfirmationId().equals(updatedConfirmation.confirmationId().toString())
                || currentRow.getVersion() != expectedVersion) {
            return false;
        }

        List<DecisionRow> currentDecisions = mapper.findDecisions(
                ownerId.value(),
                updatedConfirmation.confirmationId().toString(),
                updatedConfirmation.reportId().toString(),
                updatedConfirmation.interviewId().toString()
        );
        Map<String, DecisionRow> currentById = currentDecisions.stream()
                .collect(Collectors.toMap(DecisionRow::getDecisionId, Function.identity()));

        List<DecisionRow> updatedDecisions = converter.toDecisionRows(updatedConfirmation);
        if (!currentById.keySet().equals(
                updatedDecisions.stream().map(DecisionRow::getDecisionId).collect(Collectors.toSet())
        )) {
            throw new IllegalStateException("数据库决定集合与确认聚合不一致");
        }

        if (mapper.updateConfirmationIfVersionMatches(
                converter.toConfirmationRow(updatedConfirmation), expectedVersion
        ) != 1) {
            return false;
        }

        for (DecisionRow updatedDecision : updatedDecisions) {
            DecisionRow currentDecision = currentById.get(updatedDecision.getDecisionId());
            if (decisionChanged(currentDecision, updatedDecision)
                    && mapper.updateDecision(updatedDecision) != 1) {
                throw new IllegalStateException("决定应用状态更新失败");
            }
        }
        return true;
    }

    private InterviewReportConfirmation load(ConfirmationRow row) {
        List<DecisionRow> decisions = mapper.findDecisions(
                row.getOwnerId(), row.getConfirmationId(), row.getReportId(), row.getInterviewId()
        );
        return converter.toDomain(row, decisions);
    }

    private boolean decisionChanged(DecisionRow current, DecisionRow updated) {
        return !Objects.equals(current.getApplicationStatus(), updated.getApplicationStatus())
                || !Objects.equals(current.getOutputReferenceId(), updated.getOutputReferenceId())
                || !Objects.equals(current.getFailureCode(), updated.getFailureCode())
                || !Objects.equals(current.getUpdatedAt(), updated.getUpdatedAt())
                || !Objects.equals(current.getFinishedAt(), updated.getFinishedAt());
    }

    private static void requireScope(ActorId ownerId, UUID interviewId, UUID reportId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(reportId, "reportId不能为空");
    }
}