package com.leo.careerforgeai.interview.application.report;

import com.leo.careerforgeai.interview.domain.InterviewReport;
import com.leo.careerforgeai.interview.domain.InterviewReportConfirmation;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @program: CareerForge-AI
 * @description: 校验报告建议全量决定并生成稳定请求指纹和待应用确认单
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Component
public class InterviewReportConfirmationFactory {

    private final JsonMapper jsonMapper;

    public InterviewReportConfirmationFactory(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
    }

    public InterviewReportConfirmation create(
            InterviewReport report,
            UUID requestId,
            long expectedReportVersion,
            List<Selection> selections,
            Instant now
    ) {
        Objects.requireNonNull(report, "report不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");
        Objects.requireNonNull(now, "now不能为空");
        requireExpectedVersion(report, expectedReportVersion);
        requireCompleteSelections(report, selections);

        UUID confirmationId = UUID.randomUUID();
        Map<UUID, Selection> selectionsBySuggestion = selections.stream()
                .collect(Collectors.toMap(Selection::suggestionId, Function.identity()));

        List<InterviewReportConfirmation.Decision> decisions = new ArrayList<>(report.suggestions().size());
        for (InterviewReport.Suggestion suggestion : report.suggestions()) {
            Selection selection = selectionsBySuggestion.get(suggestion.suggestionId());
            UUID decisionId = UUID.randomUUID();
            InterviewReportConfirmation.Decision decision =
                    selection.decisionType() == InterviewReportConfirmation.DecisionType.CONFIRMED
                            ? InterviewReportConfirmation.Decision.confirmed(
                                    decisionId,
                                    confirmationId,
                                    suggestion.suggestionId(),
                                    report.reportId(),
                                    report.interviewId(),
                                    report.ownerId(),
                                    now
                            )
                            : InterviewReportConfirmation.Decision.rejected(
                                    decisionId,
                                    confirmationId,
                                    suggestion.suggestionId(),
                                    report.reportId(),
                                    report.interviewId(),
                                    report.ownerId(),
                                    now
                            );
            decisions.add(decision);
        }

        return InterviewReportConfirmation.pendingApplication(
                confirmationId,
                report.reportId(),
                report.interviewId(),
                report.ownerId(),
                requestId,
                fingerprint(report.interviewId(), report.reportId(), expectedReportVersion, selections),
                expectedReportVersion,
                decisions,
                now
        );
    }

    public String fingerprint(
            UUID interviewId,
            UUID reportId,
            long expectedReportVersion,
            List<Selection> selections
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(reportId, "reportId不能为空");
        if (expectedReportVersion < 0) throw new IllegalArgumentException("expectedReportVersion不能小于0");
        List<Selection> normalizedSelections = requireUniqueSelections(selections).stream()
                .sorted(Comparator.comparing(selection -> selection.suggestionId().toString()))
                .toList();

        List<Map<String, String>> canonicalDecisions = normalizedSelections.stream().map(selection -> {
            Map<String, String> decision = new LinkedHashMap<>();
            decision.put("suggestionId", selection.suggestionId().toString());
            decision.put("decisionType", selection.decisionType().name());
            return decision;
        }).toList();

        Map<String, Object> canonicalRequest = new LinkedHashMap<>();
        canonicalRequest.put("schemaVersion", 1);
        canonicalRequest.put("interviewId", interviewId.toString());
        canonicalRequest.put("reportId", reportId.toString());
        canonicalRequest.put("expectedReportVersion", expectedReportVersion);
        canonicalRequest.put("decisions", canonicalDecisions);

        try {
            return sha256(jsonMapper.writeValueAsString(canonicalRequest));
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化报告确认请求失败", exception);
        }
    }

    private void requireExpectedVersion(InterviewReport report, long expectedReportVersion) {
        if (report.version() != expectedReportVersion) {
            throw new InterviewReportConfirmationException(
                    InterviewReportConfirmationException.Reason.REPORT_VERSION_CONFLICT,
                    "报告版本已经变化，请刷新后重新确认"
            );
        }
    }

    private void requireCompleteSelections(InterviewReport report, List<Selection> selections) {
        List<Selection> uniqueSelections = requireUniqueSelections(selections);
        Set<UUID> expectedSuggestionIds = report.suggestions().stream()
                .map(InterviewReport.Suggestion::suggestionId)
                .collect(Collectors.toSet());
        Set<UUID> actualSuggestionIds = uniqueSelections.stream()
                .map(Selection::suggestionId)
                .collect(Collectors.toSet());

        if (!actualSuggestionIds.equals(expectedSuggestionIds)) {
            throw new IllegalArgumentException("必须对报告中的全部建议逐项确认或拒绝");
        }
    }

    private List<Selection> requireUniqueSelections(List<Selection> selections) {
        Objects.requireNonNull(selections, "selections不能为空");
        if (selections.size() > 20) throw new IllegalArgumentException("selections数量不能超过20");
        List<Selection> copy = List.copyOf(selections);
        Set<UUID> suggestionIds = new HashSet<>();
        for (Selection selection : copy) {
            Objects.requireNonNull(selection, "selection不能为空");
            if (!suggestionIds.add(selection.suggestionId())) {
                throw new IllegalArgumentException("同一建议不能重复提交决定");
            }
        }
        return copy;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 表示用户对一条报告建议提交的决定
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param suggestionId 报告建议UUID
     * @param decisionType 用户决定类型
     */
    public record Selection(
            UUID suggestionId,
            InterviewReportConfirmation.DecisionType decisionType
    ) {

        public Selection {
            Objects.requireNonNull(suggestionId, "suggestionId不能为空");
            Objects.requireNonNull(decisionType, "decisionType不能为空");
        }
    }
}