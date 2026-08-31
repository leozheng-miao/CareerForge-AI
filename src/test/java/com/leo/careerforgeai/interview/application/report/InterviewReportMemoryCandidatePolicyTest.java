package com.leo.careerforgeai.interview.application.report;

import com.leo.careerforgeai.interview.application.model.contract.InterviewReportDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportInput;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportSuggestionDraft;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.leo.careerforgeai.interview.domain.EvidenceConsistencyVerdict;
import java.util.Map;

/**
 * @program: CareerForge-AI
 * @description: 验证报告Memory候选只能来自Java授权白名单且默认拒绝模型自创能力
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
class InterviewReportMemoryCandidatePolicyTest {

    private final InterviewReportMemoryCandidatePolicy policy = new InterviewReportMemoryCandidatePolicy();

    @Test
    void shouldKeepOnlyExactJavaAuthorizedMemoryCandidate() {
        InterviewReportInput input = input(List.of(
                new InterviewReportInput.AllowedMemoryCandidate(
                        "并发准入",
                        "面试回答已覆盖：使用owner级Semaphore限制并发并在finally释放许可。"
                )
        ));
        InterviewReportDraft draft = draft(List.of(
                new InterviewReportSuggestionDraft.MemoryCandidate(
                        "并发准入",
                        "面试回答已覆盖：使用owner级Semaphore限制并发并在finally释放许可。"
                ),
                new InterviewReportSuggestionDraft.MemoryCandidate(
                        "容量压测",
                        "能够稳定承载一万并发请求。"
                ),
                new InterviewReportSuggestionDraft.MemoryCandidate(
                        "并发准入",
                        "精通所有分布式并发控制方案。"
                )
        ));

        InterviewReportDraft filtered = policy.filter(input, draft);

        assertThat(filtered.proposedMemoryCandidates()).containsExactly(
                new InterviewReportSuggestionDraft.MemoryCandidate(
                        "并发准入",
                        "面试回答已覆盖：使用owner级Semaphore限制并发并在finally释放许可。"
                )
        );
        assertThat(filtered.proposedTrainingPlanAdjustments())
                .isEqualTo(draft.proposedTrainingPlanAdjustments());
    }

    @Test
    void shouldRemoveAllMemoryCandidatesWhenJavaWhitelistIsEmpty() {
        InterviewReportInput input = input(List.of());
        InterviewReportDraft draft = draft(List.of(
                new InterviewReportSuggestionDraft.MemoryCandidate(
                        "MySQL事实源",
                        "能够设计以MySQL为业务事实源的系统。"
                ),
                new InterviewReportSuggestionDraft.MemoryCandidate(
                        "Checkpoint恢复",
                        "能够设计跨进程Checkpoint恢复机制。"
                )
        ));

        InterviewReportDraft filtered = policy.filter(input, draft);

        assertThat(filtered.proposedMemoryCandidates()).isEmpty();
        assertThat(filtered.improvementActions()).isEqualTo(draft.improvementActions());
        assertThat(filtered.proposedTrainingPlanAdjustments())
                .isEqualTo(draft.proposedTrainingPlanAdjustments());
    }

    @Test
    void shouldDeriveExactCandidatesFromStrongGroundedAnswer() {
        List<InterviewReportInput.AllowedMemoryCandidate> candidates = policy.deriveAllowedCandidates(
                List.of("Java并发", "资源释放"),
                Map.of("CORRECTNESS", 4, "DEPTH", 3, "FAILURE_HANDLING", 3),
                EvidenceConsistencyVerdict.SUPPORTED,
                "项目使用owner级Semaphore限制并发，并在finally中释放许可。"
        );

        assertThat(candidates).extracting(InterviewReportInput.AllowedMemoryCandidate::skillName)
                .containsExactly("Java并发", "资源释放");
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.content()).contains("原文摘录");
            assertThat(candidate.content()).contains("owner级Semaphore");
            assertThat(candidate.content()).contains("finally");
        });
    }

    @Test
    void shouldRejectWeakPartialUnsupportedAndContradictedAnswers() {
        Map<String, Integer> strongScores =
                Map.of("CORRECTNESS", 4, "DEPTH", 3, "FAILURE_HANDLING", 3);
        Map<String, Integer> weakScores =
                Map.of("CORRECTNESS", 4, "DEPTH", 2, "FAILURE_HANDLING", 3);

        assertThat(policy.deriveAllowedCandidates(
                List.of("并发准入"),
                weakScores,
                EvidenceConsistencyVerdict.SUPPORTED,
                "使用Semaphore限制并发。"
        )).isEmpty();
        assertThat(policy.deriveAllowedCandidates(
                List.of("并发准入"),
                strongScores,
                EvidenceConsistencyVerdict.PARTIALLY_SUPPORTED,
                "使用Semaphore限制并发，但压测数据没有证据。"
        )).isEmpty();
        assertThat(policy.deriveAllowedCandidates(
                List.of("MySQL事实源"),
                strongScores,
                EvidenceConsistencyVerdict.UNSUPPORTED,
                "Redis是唯一事实源。"
        )).isEmpty();
        assertThat(policy.deriveAllowedCandidates(
                List.of("MySQL事实源"),
                strongScores,
                EvidenceConsistencyVerdict.CONTRADICTED,
                "Redis是唯一事实源。"
        )).isEmpty();
    }

    @Test
    void shouldDeriveAndFilterOnlyJavaAuthorizedStrengths() {
        List<String> allowed = policy.deriveAllowedStrengths(
                Map.of("CORRECTNESS", 4, "DEPTH", 3, "FAILURE_HANDLING", 3),
                EvidenceConsistencyVerdict.SUPPORTED,
                List.of("能够说明Semaphore并发准入", "能够说明finally释放许可")
        );
        InterviewReportInput input = new InterviewReportInput(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Java AI应用开发工程师",
                List.of("回答与评审摘要"),
                allowed,
                List.of()
        );
        InterviewReportDraft draft = new InterviewReportDraft(
                List.of(
                        "能够说明Semaphore并发准入",
                        "精通所有分布式并发控制方案"
                ),
                List.of(),
                List.of(),
                List.of("补充多实例边界"),
                List.of(),
                List.of()
        );

        InterviewReportDraft filtered = policy.filter(input, draft);

        assertThat(filtered.strengths()).containsExactly("能够说明Semaphore并发准入");
    }

    @Test
    void shouldRejectStrengthsForWeakOrContradictedAnswer() {
        assertThat(policy.deriveAllowedStrengths(
                Map.of("CORRECTNESS", 4, "DEPTH", 2, "FAILURE_HANDLING", 3),
                EvidenceConsistencyVerdict.SUPPORTED,
                List.of("提到Semaphore")
        )).isEmpty();
        assertThat(policy.deriveAllowedStrengths(
                Map.of("CORRECTNESS", 4, "DEPTH", 4, "FAILURE_HANDLING", 4),
                EvidenceConsistencyVerdict.CONTRADICTED,
                List.of("将Redis作为最终真相源")
        )).isEmpty();
    }

    @Test
    void shouldRemoveTrainingPlanAdjustmentsWhenGapSnapshotIsUnavailable() {
        InterviewReportInput input = new InterviewReportInput(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Java AI应用开发工程师",
                List.of("回答与评审摘要"),
                List.of(),
                List.of(),
                false
        );
        InterviewReportDraft draft = draft(List.of());

        InterviewReportDraft filtered = policy.filter(input, draft);

        assertThat(filtered.proposedTrainingPlanAdjustments()).isEmpty();
        assertThat(filtered.technicalGaps()).isEqualTo(draft.technicalGaps());
        assertThat(filtered.improvementActions()).isEqualTo(draft.improvementActions());
    }

    private InterviewReportInput input(
            List<InterviewReportInput.AllowedMemoryCandidate> allowedCandidates
    ) {
        return new InterviewReportInput(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Java AI应用开发工程师",
                List.of("回答与评审摘要"),
                List.of(),
                allowedCandidates,
                true
        );
    }

    private InterviewReportDraft draft(
            List<InterviewReportSuggestionDraft.MemoryCandidate> memoryCandidates
    ) {
        return new InterviewReportDraft(
                List.of(),
                List.of("需要补充可靠性设计"),
                List.of(),
                List.of("补充失败恢复实验"),
                memoryCandidates,
                List.of(new InterviewReportSuggestionDraft.TrainingPlanAdjustment(
                        "可靠性",
                        "增加失败恢复专项训练。"
                ))
        );
    }
}