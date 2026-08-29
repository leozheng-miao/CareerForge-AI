package com.leo.careerforgeai.interview.infrastructure.persistence.converter;

import com.leo.careerforgeai.interview.domain.InterviewReportConfirmation;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportConfirmationPersistenceModels.ConfirmationRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportConfirmationPersistenceModels.DecisionRow;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 转换报告确认聚合、确认单数据库行和决定数据库行
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Component
public class InterviewReportConfirmationPersistenceConverter {

    public ConfirmationRow toConfirmationRow(InterviewReportConfirmation confirmation) {
        Objects.requireNonNull(confirmation, "confirmation不能为空");
        ConfirmationRow row = new ConfirmationRow();
        row.setConfirmationId(confirmation.confirmationId().toString());
        row.setReportId(confirmation.reportId().toString());
        row.setInterviewId(confirmation.interviewId().toString());
        row.setOwnerId(confirmation.ownerId().value());
        row.setRequestId(confirmation.requestId().toString());
        row.setRequestFingerprint(confirmation.requestFingerprint());
        row.setExpectedReportVersion(confirmation.expectedReportVersion());
        row.setConfirmationStatus(confirmation.status().name());
        row.setFailureCode(confirmation.failureCode());
        row.setVersion(confirmation.version());
        row.setCreatedAt(confirmation.createdAt());
        row.setUpdatedAt(confirmation.updatedAt());
        row.setApplicationFinishedAt(confirmation.applicationFinishedAt());
        return row;
    }

    public List<DecisionRow> toDecisionRows(InterviewReportConfirmation confirmation) {
        Objects.requireNonNull(confirmation, "confirmation不能为空");
        return confirmation.decisions().stream().map(this::toDecisionRow).toList();
    }

    public InterviewReportConfirmation toDomain(
            ConfirmationRow confirmation,
            List<DecisionRow> decisions
    ) {
        Objects.requireNonNull(confirmation, "confirmation不能为空");
        Objects.requireNonNull(decisions, "decisions不能为空");
        return new InterviewReportConfirmation(
                UUID.fromString(confirmation.getConfirmationId()),
                UUID.fromString(confirmation.getReportId()),
                UUID.fromString(confirmation.getInterviewId()),
                new ActorId(confirmation.getOwnerId()),
                UUID.fromString(confirmation.getRequestId()),
                confirmation.getRequestFingerprint(),
                confirmation.getExpectedReportVersion(),
                InterviewReportConfirmation.Status.valueOf(confirmation.getConfirmationStatus()),
                decisions.stream().map(this::toDecision).toList(),
                confirmation.getFailureCode(),
                confirmation.getVersion(),
                confirmation.getCreatedAt(),
                confirmation.getUpdatedAt(),
                confirmation.getApplicationFinishedAt()
        );
    }

    private DecisionRow toDecisionRow(InterviewReportConfirmation.Decision decision) {
        DecisionRow row = new DecisionRow();
        row.setDecisionId(decision.decisionId().toString());
        row.setConfirmationId(decision.confirmationId().toString());
        row.setSuggestionId(decision.suggestionId().toString());
        row.setReportId(decision.reportId().toString());
        row.setInterviewId(decision.interviewId().toString());
        row.setOwnerId(decision.ownerId().value());
        row.setDecisionType(decision.decisionType().name());
        row.setApplicationStatus(decision.applicationStatus().name());
        row.setOutputReferenceId(
                decision.outputReferenceId() == null ? null : decision.outputReferenceId().toString()
        );
        row.setFailureCode(decision.failureCode());
        row.setCreatedAt(decision.createdAt());
        row.setUpdatedAt(decision.updatedAt());
        row.setFinishedAt(decision.finishedAt());
        return row;
    }

    private InterviewReportConfirmation.Decision toDecision(DecisionRow row) {
        return new InterviewReportConfirmation.Decision(
                UUID.fromString(row.getDecisionId()),
                UUID.fromString(row.getConfirmationId()),
                UUID.fromString(row.getSuggestionId()),
                UUID.fromString(row.getReportId()),
                UUID.fromString(row.getInterviewId()),
                new ActorId(row.getOwnerId()),
                InterviewReportConfirmation.DecisionType.valueOf(row.getDecisionType()),
                InterviewReportConfirmation.ApplicationStatus.valueOf(row.getApplicationStatus()),
                row.getOutputReferenceId() == null ? null : UUID.fromString(row.getOutputReferenceId()),
                row.getFailureCode(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getFinishedAt()
        );
    }
}