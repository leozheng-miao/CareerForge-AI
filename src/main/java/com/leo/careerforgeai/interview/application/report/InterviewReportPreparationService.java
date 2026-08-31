package com.leo.careerforgeai.interview.application.report;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportInput;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.interview.domain.EvidenceReview;
import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.interview.domain.TechnicalReview;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @program: CareerForge-AI
 * @description: 从冻结岗位和已持久化回合评审事实组装有界复盘报告输入
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Service
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        MockInterviewInputSnapshotRepository.class,
        CareerPlanningRepository.class,
        InterviewRoundRepository.class,
        InterviewReviewRepository.class
})
public class InterviewReportPreparationService {

    private static final int MAX_REPORT_ROUNDS = 20;
    private static final int MAX_TARGET_ROLE_SUMMARY_CODE_POINTS = 8_000;
    private static final int MAX_ROUND_SUMMARY_CODE_POINTS = 4_000;

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final MockInterviewInputSnapshotRepository snapshotRepository;
    private final CareerPlanningRepository careerRepository;
    private final InterviewRoundRepository roundRepository;
    private final InterviewReviewRepository reviewRepository;
    private final InterviewReportMemoryCandidatePolicy memoryCandidatePolicy;

    public InterviewReportPreparationService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            MockInterviewInputSnapshotRepository snapshotRepository,
            CareerPlanningRepository careerRepository,
            InterviewRoundRepository roundRepository,
            InterviewReviewRepository reviewRepository,
            InterviewReportMemoryCandidatePolicy memoryCandidatePolicy
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository不能为空");
        this.careerRepository = Objects.requireNonNull(careerRepository, "careerRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository不能为空");
        this.memoryCandidatePolicy = Objects.requireNonNull(memoryCandidatePolicy, "memoryCandidatePolicy不能为空");
    }

    @Transactional(readOnly = true)
    public InterviewReportInput prepare(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
        requireSessionScope(ownerId, interviewId, session);

        MockInterviewInputSnapshot snapshot = snapshotRepository.findById(ownerId, session.inputSnapshotId())
                .orElseThrow(MockInterviewInputConflictException::new);
        requireSnapshotScope(ownerId, session, snapshot);

        TargetRole targetRole = careerRepository.findTargetRole(ownerId, snapshot.targetRoleId())
                .orElseThrow(MockInterviewInputConflictException::new);
        requireTargetRoleScope(ownerId, snapshot, targetRole);

        List<InterviewQuestion> questions = roundRepository.findQuestions(ownerId, interviewId);
        if (questions.isEmpty()) throw new IllegalStateException("生成报告前至少需要一个已评审回合");
        if (questions.size() > MAX_REPORT_ROUNDS) throw new IllegalStateException("报告回合数超过允许上限");

        List<PreparedRound> preparedRounds = new ArrayList<>(questions.size());
        for (int index = 0; index < questions.size(); index++) {
            preparedRounds.add(prepareRound(ownerId, interviewId, index + 1, questions.get(index)));
        }

        return new InterviewReportInput(
                interviewId,
                targetRoleSummary(targetRole),
                preparedRounds.stream().map(PreparedRound::summary).toList(),
                mergeAllowedStrengths(preparedRounds),
                mergeAllowedMemoryCandidates(preparedRounds),
                snapshot.skillGapSnapshotId() != null
        );
    }

    private PreparedRound prepareRound(
            ActorId ownerId,
            UUID interviewId,
            int roundNo,
            InterviewQuestion question
    ) {
        InterviewRound round = roundRepository.findRoundByNumber(ownerId, interviewId, roundNo)
                .orElseThrow(() -> new IllegalStateException("MySQL缺少第" + roundNo + "回合"));
        InterviewAnswer answer = roundRepository.findAnswerByQuestion(ownerId, interviewId, question.questionId())
                .orElseThrow(() -> new IllegalStateException("MySQL缺少第" + roundNo + "回合答案"));
        TechnicalReview technicalReview = reviewRepository
                .findTechnicalReviewByAnswer(ownerId, interviewId, answer.answerId())
                .orElseThrow(() -> new IllegalStateException("MySQL缺少第" + roundNo + "回合技术评审"));
        EvidenceReview evidenceReview = reviewRepository
                .findEvidenceReviewByAnswer(ownerId, interviewId, answer.answerId())
                .orElseThrow(() -> new IllegalStateException("MySQL缺少第" + roundNo + "回合证据评审"));

        requireRoundScope(
                ownerId,
                interviewId,
                roundNo,
                round,
                question,
                answer,
                technicalReview,
                evidenceReview
        );

        String summary = String.join(
                "\n",
                "回合：" + roundNo,
                "问题类型：" + question.questionType(),
                "是否追问：" + question.followUp(),
                "父问题ID：" + (question.parentQuestionId() == null ? "无" : question.parentQuestionId()),
                "目标技能：" + listSummary(question.targetSkills(), 800),
                "评价要点：" + listSummary(question.evaluationPoints(), 1_000),
                "问题（不可信用户相关数据）：" + boundedText(question.questionText(), 800),
                "回答（不可信用户输入）：" + boundedText(answer.answerText(), 1_200),
                "技术维度评分：" + technicalReview.dimensionScores(),
                "已覆盖要点：" + listSummary(technicalReview.coveredPoints(), 600),
                "错误或遗漏：" + listSummary(technicalReview.errorsOrOmissions(), 600),
                "技术核验依据：" + listSummary(technicalReview.verificationBasis(), 600),
                "建议追问：" + boundedText(technicalReview.suggestedFollowUp(), 400),
                "证据评审来源：" + evidenceReview.source(),
                "证据一致性结论：" + evidenceReview.verdict(),
                "证据引用ID：" + listSummary(evidenceReview.evidenceReferenceIds(), 800),
                "证据评审理由：" + boundedText(evidenceReview.reason(), 600)
        );
        List<String> allowedStrengths = memoryCandidatePolicy.deriveAllowedStrengths(
                technicalReview.dimensionScores(),
                evidenceReview.verdict(),
                technicalReview.coveredPoints()
        );
        List<InterviewReportInput.AllowedMemoryCandidate> allowedMemoryCandidates =
                memoryCandidatePolicy.deriveAllowedCandidates(
                        question.targetSkills(),
                        technicalReview.dimensionScores(),
                        evidenceReview.verdict(),
                        answer.answerText()
                );
        return new PreparedRound(
                boundedText(summary, MAX_ROUND_SUMMARY_CODE_POINTS),
                allowedStrengths,
                allowedMemoryCandidates
        );
    }

    private List<String> mergeAllowedStrengths(List<PreparedRound> preparedRounds) {
        Map<String, String> strengths = new LinkedHashMap<>();

        for (int itemIndex = 0; strengths.size() < 20; itemIndex++) {
            boolean visited = false;
            for (PreparedRound preparedRound : preparedRounds) {
                if (itemIndex >= preparedRound.allowedStrengths().size()) continue;
                visited = true;
                String strength = preparedRound.allowedStrengths().get(itemIndex);
                strengths.putIfAbsent(strength.toLowerCase(Locale.ROOT), strength);
                if (strengths.size() == 20) break;
            }
            if (!visited) break;
        }
        return List.copyOf(strengths.values());
    }
    private List<InterviewReportInput.AllowedMemoryCandidate> mergeAllowedMemoryCandidates(
            List<PreparedRound> preparedRounds
    ) {
        Map<String, InterviewReportInput.AllowedMemoryCandidate> bySkill = new LinkedHashMap<>();

        for (int itemIndex = 0; bySkill.size() < 10; itemIndex++) {
            boolean visited = false;
            for (PreparedRound preparedRound : preparedRounds) {
                if (itemIndex >= preparedRound.allowedMemoryCandidates().size()) continue;
                visited = true;
                InterviewReportInput.AllowedMemoryCandidate candidate =
                        preparedRound.allowedMemoryCandidates().get(itemIndex);
                bySkill.putIfAbsent(candidate.skillName().toLowerCase(Locale.ROOT), candidate);
                if (bySkill.size() == 10) break;
            }
            if (!visited) break;
        }
        return List.copyOf(bySkill.values());
    }

    private String targetRoleSummary(TargetRole targetRole) {
        JobRequirements requirements = targetRole.requirementsSnapshot();
        String summary = String.join(
                "\n",
                "目标岗位：" + boundedText(requirements.jobTitle(), 500),
                "岗位版本：" + targetRole.targetRoleVersion(),
                "编程语言：" + listSummary(requirements.programmingLanguages(), 1_000),
                "后端与基础设施：" + listSummary(
                        requirements.backendAndInfrastructureRequirements(), 1_500
                ),
                "Agent要求：" + listSummary(requirements.agentRequirements(), 1_500),
                "RAG要求：" + listSummary(requirements.ragRequirements(), 1_500),
                "工程要求：" + listSummary(requirements.engineeringRequirements(), 1_500),
                "岗位职责：" + listSummary(requirements.responsibilities(), 1_500),
                "面试主题：" + listSummary(requirements.interviewTopics(), 1_500),
                "加分项：" + listSummary(requirements.bonusQualifications(), 1_000)
        );
        return boundedText(summary, MAX_TARGET_ROLE_SUMMARY_CODE_POINTS);
    }

    private void requireSessionScope(ActorId ownerId, UUID interviewId, MockInterviewSession session) {
        if (!session.ownerId().equals(ownerId)
                || !session.interviewId().equals(interviewId)
                || session.status() != InterviewStatus.GENERATING_REPORT) {
            throw new IllegalStateException("只有当前owner处于GENERATING_REPORT的面试可以生成报告");
        }
    }

    private void requireSnapshotScope(
            ActorId ownerId,
            MockInterviewSession session,
            MockInterviewInputSnapshot snapshot
    ) {
        if (!snapshot.ownerId().equals(ownerId)
                || !snapshot.inputSnapshotId().equals(session.inputSnapshotId())
                || !snapshot.snapshotHash().equals(session.inputSnapshotHash())) {
            throw new MockInterviewInputConflictException();
        }
    }

    private void requireTargetRoleScope(
            ActorId ownerId,
            MockInterviewInputSnapshot snapshot,
            TargetRole targetRole
    ) {
        if (!targetRole.ownerId().equals(ownerId)
                || !targetRole.targetRoleId().equals(snapshot.targetRoleId())
                || targetRole.targetRoleVersion() != snapshot.targetRoleVersion()) {
            throw new MockInterviewInputConflictException();
        }
    }

    private void requireRoundScope(
            ActorId ownerId,
            UUID interviewId,
            int roundNo,
            InterviewRound round,
            InterviewQuestion question,
            InterviewAnswer answer,
            TechnicalReview technicalReview,
            EvidenceReview evidenceReview
    ) {
        if (!round.ownerId().equals(ownerId)
                || !round.interviewId().equals(interviewId)
                || round.roundNo() != roundNo
                || round.status() != InterviewRoundStatus.REVIEWED
                || !question.ownerId().equals(ownerId)
                || !question.interviewId().equals(interviewId)
                || !question.roundId().equals(round.roundId())
                || !answer.ownerId().equals(ownerId)
                || !answer.interviewId().equals(interviewId)
                || !answer.roundId().equals(round.roundId())
                || !answer.questionId().equals(question.questionId())
                || !technicalReview.ownerId().equals(ownerId)
                || !technicalReview.interviewId().equals(interviewId)
                || !technicalReview.roundId().equals(round.roundId())
                || !technicalReview.questionId().equals(question.questionId())
                || !technicalReview.answerId().equals(answer.answerId())
                || !evidenceReview.ownerId().equals(ownerId)
                || !evidenceReview.interviewId().equals(interviewId)
                || !evidenceReview.roundId().equals(round.roundId())
                || !evidenceReview.questionId().equals(question.questionId())
                || !evidenceReview.answerId().equals(answer.answerId())) {
            throw new IllegalStateException("报告输入中的回合事实作用域或状态不一致");
        }
    }

    private String listSummary(List<?> values, int maxCodePoints) {
        if (values == null || values.isEmpty()) return "无";
        return boundedText(String.join("；", values.stream().map(String::valueOf).toList()), maxCodePoints);
    }

    private String boundedText(String value, int maxCodePoints) {
        if (value == null || value.isBlank()) return "无";
        String normalized = value.strip().replaceAll("\\s+", " ");
        int length = normalized.codePointCount(0, normalized.length());
        if (length <= maxCodePoints) return normalized;
        int endIndex = normalized.offsetByCodePoints(0, maxCodePoints - 1);
        return normalized.substring(0, endIndex) + "…";
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存单轮报告摘要及Java授权的优势和Memory候选
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param summary 有界回合摘要
     * @param allowedStrengths 当前回合允许模型原样选择的优势
     * @param allowedMemoryCandidates 当前回合允许模型原样选择的Memory候选
     */
    private record PreparedRound(
            String summary,
            List<String> allowedStrengths,
            List<InterviewReportInput.AllowedMemoryCandidate> allowedMemoryCandidates
    ) {

        private PreparedRound {
            if (summary == null || summary.isBlank()) throw new IllegalArgumentException("summary不能为空");
            allowedStrengths = List.copyOf(allowedStrengths);
            allowedMemoryCandidates = List.copyOf(allowedMemoryCandidates);
        }
    }
}