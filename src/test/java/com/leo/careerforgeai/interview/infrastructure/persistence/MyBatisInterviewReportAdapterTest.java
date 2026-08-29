package com.leo.careerforgeai.interview.infrastructure.persistence;

import com.leo.careerforgeai.interview.domain.InterviewReport;
import com.leo.careerforgeai.interview.infrastructure.persistence.adapter.MyBatisInterviewReportAdapter;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.InterviewReportPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportPersistenceModels.ReportRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportPersistenceModels.SuggestionRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.InterviewReportFactMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

/**
 * @program: CareerForge-AI
 * @description: 验证报告聚合认领、建议幂等保存和报告状态乐观锁更新
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
class MyBatisInterviewReportAdapterTest {

    private static final ActorId OWNER = new ActorId("report-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SUGGESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    private InterviewReportFactMapper mapper;
    private InterviewReportPersistenceConverter converter;
    private MyBatisInterviewReportAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = mock(InterviewReportFactMapper.class);
        converter = new InterviewReportPersistenceConverter(JsonMapper.builder().build());
        adapter = new MyBatisInterviewReportAdapter(mapper, converter);
    }

    @Test
    void shouldClaimReportWithSuggestionsAndCasFinalDecision() {
        InterviewReport candidate = report(REPORT_ID, SUGGESTION_ID);
        ReportRow reportRow = converter.toReportRow(candidate);
        List<SuggestionRow> suggestionRows = converter.toSuggestionRows(candidate);

        when(mapper.findByInterview(OWNER.value(), INTERVIEW_ID.toString())).thenReturn(reportRow);
        when(mapper.findSuggestions(
                OWNER.value(), INTERVIEW_ID.toString(), REPORT_ID.toString()
        )).thenReturn(suggestionRows);
        when(mapper.updateIfVersionMatches(
                argThat(row -> row.getReportId().equals(REPORT_ID.toString())
                        && row.getReportStatus().equals(InterviewReport.Status.DECIDED.name())
                        && row.getVersion() == 1),
                eq(0L)
        )).thenReturn(1);

        InterviewReport stored = adapter.claim(candidate);
        InterviewReport decided = stored.decide(NOW.plus(1, ChronoUnit.SECONDS));
        boolean updated = adapter.updateIfVersionMatches(OWNER, decided, stored.version());

        assertThat(stored).isEqualTo(candidate);
        assertThat(updated).isTrue();
        verify(mapper).claimReport(argThat(row ->
                row.getReportId().equals(REPORT_ID.toString())
                        && row.getInputHash().equals("a".repeat(64))
                        && row.getReportStatus().equals(InterviewReport.Status.PENDING_CONFIRMATION.name())
        ));
        verify(mapper).claimSuggestions(argThat(rows ->
                rows.size() == 1
                        && rows.getFirst().getSuggestionId().equals(SUGGESTION_ID.toString())
                        && rows.getFirst().getSuggestionType()
                        .equals(InterviewReport.SuggestionType.MEMORY_CANDIDATE.name())
                        && rows.getFirst().getSuggestionPayloadJson().contains("\"skillName\":\"Java并发\"")
        ));
    }

    @Test
    void shouldReturnExistingReportWithoutWritingCandidateSuggestions() {
        InterviewReport candidate = report(REPORT_ID, SUGGESTION_ID);
        UUID existingReportId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID existingSuggestionId = UUID.fromString("00000000-0000-0000-0000-000000000012");
        InterviewReport existing = report(existingReportId, existingSuggestionId);

        when(mapper.findByInterview(OWNER.value(), INTERVIEW_ID.toString()))
                .thenReturn(converter.toReportRow(existing));
        when(mapper.findSuggestions(
                OWNER.value(), INTERVIEW_ID.toString(), existingReportId.toString()
        )).thenReturn(converter.toSuggestionRows(existing));

        InterviewReport stored = adapter.claim(candidate);

        assertThat(stored).isEqualTo(existing);
        verify(mapper).claimReport(argThat(row -> row.getReportId().equals(REPORT_ID.toString())));
        verify(mapper, never()).claimSuggestions(argThat(rows -> !rows.isEmpty()));
    }

    private InterviewReport report(UUID reportId, UUID suggestionId) {
        String content = "保留候选人的Java并发实践作为长期能力证据。";
        InterviewReport.MemoryCandidatePayload payload =
                new InterviewReport.MemoryCandidatePayload("Java并发", content);
        InterviewReport.Suggestion suggestion = new InterviewReport.Suggestion(
                suggestionId,
                reportId,
                INTERVIEW_ID,
                OWNER,
                InterviewReport.SuggestionType.MEMORY_CANDIDATE,
                1,
                content,
                payload,
                "c".repeat(64),
                NOW
        );
        return InterviewReport.pendingConfirmation(
                reportId,
                INTERVIEW_ID,
                OWNER,
                List.of("能够说明虚拟线程的适用边界。"),
                List.of("对结构化并发的取消传播理解不足。"),
                List.of("项目证据缺少可复现的性能数据。"),
                List.of("补充虚拟线程与平台线程的对照实验。"),
                List.of(suggestion),
                "report-request-1",
                "report-coach-v2",
                "a".repeat(64),
                "b".repeat(64),
                NOW
        );
    }
}