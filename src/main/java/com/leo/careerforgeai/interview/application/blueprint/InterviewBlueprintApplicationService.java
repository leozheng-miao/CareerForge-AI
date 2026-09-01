package com.leo.careerforgeai.interview.application.blueprint;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.interview.domain.round.InterviewBlueprint;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceStatus;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceType;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionInput;

import java.util.Collections;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.domain.round.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.execution.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.round.InterviewRound;
import com.leo.careerforgeai.interview.domain.round.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.review.TechnicalReview;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;

/**
 * @program: CareerForge-AI
 * @description: 从当前owner的冻结MySQL事实重建并生成确定性面试蓝图
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
@Service
@ConditionalOnBean({
        MockInterviewSessionRepository.class,
        MockInterviewInputSnapshotRepository.class,
        CareerPlanningRepository.class,
        PersonalEvidenceArtifactRepository.class,
        InterviewRoundRepository.class,
        InterviewReviewRepository.class
})
public class InterviewBlueprintApplicationService {

    private static final int MAX_BLUEPRINT_SKILLS = 20;
    private static final int MAX_SKILL_LENGTH = 100;
    private static final int MAX_QUESTION_EVIDENCE_CHUNKS = 6;

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final MockInterviewInputSnapshotRepository snapshotRepository;
    private final CareerPlanningRepository careerRepository;
    private final PersonalEvidenceArtifactRepository evidenceRepository;
    private final InterviewBlueprintPlanner planner;
    private final InterviewRoundRepository roundRepository;
    private final InterviewReviewRepository reviewRepository;

    public InterviewBlueprintApplicationService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            MockInterviewInputSnapshotRepository snapshotRepository,
            CareerPlanningRepository careerRepository,
            PersonalEvidenceArtifactRepository evidenceRepository,
            InterviewRoundRepository roundRepository,
            InterviewReviewRepository reviewRepository,
            InterviewBlueprintPlanner planner
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository不能为空");
        this.careerRepository = Objects.requireNonNull(careerRepository, "careerRepository不能为空");
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository不能为空");
        this.planner = Objects.requireNonNull(planner, "planner不能为空");
    }

    @Transactional(readOnly = true)
    public InterviewBlueprint plan(UUID interviewId) {
        return prepare(interviewId).blueprint();
    }

    @Transactional(readOnly = true)
    public InterviewQuestionInput prepareFirstQuestion(UUID interviewId) {
        PlanningContext context = prepare(interviewId);
        InterviewBlueprint.QuestionPlan firstQuestion = context.blueprint().questionAt(1);

        return new InterviewQuestionInput(
                context.session().interviewId(),
                1,
                context.session().mode(),
                firstQuestion.questionType(),
                firstQuestion.difficulty(),
                blueprintSummary(context.blueprint()),
                targetRoleSummary(context.targetRole().requirementsSnapshot()),
                evidenceByChunkId(context.artifacts()),
                List.of(),
                firstQuestion.currentRoundGoal()
        );
    }

    @Transactional(readOnly = true)
    public InterviewQuestionInput prepareNextQuestion(
            UUID interviewId,
            InterviewRouteDecision routeDecision
    ) {
        Objects.requireNonNull(routeDecision, "routeDecision不能为空");
        if (routeDecision != InterviewRouteDecision.FOLLOW_UP
                && routeDecision != InterviewRouteDecision.NEXT_QUESTION) {
            throw new IllegalArgumentException("只允许准备FOLLOW_UP或NEXT_QUESTION");
        }

        PlanningContext context = prepare(interviewId);
        MockInterviewSession session = context.session();
        if (session.status() != InterviewStatus.GENERATING_QUESTION) {
            throw new IllegalStateException("只有GENERATING_QUESTION状态可以准备下一轮问题");
        }

        ActorId ownerId = session.ownerId();
        List<InterviewQuestion> questions = roundRepository.findQuestions(ownerId, interviewId);
        if (questions.isEmpty()) throw new IllegalStateException("生成下一轮问题前必须存在已完成问题");
        if (questions.size() >= session.budgetPolicy().maxQuestions()) {
            throw new IllegalStateException("已达到最大问题数，不能继续生成问题");
        }

        int previousRoundNo = questions.size();
        int nextRoundNo = previousRoundNo + 1;
        InterviewQuestion previousQuestion = questions.get(previousRoundNo - 1);
        InterviewRound previousRound = roundRepository.findRoundByNumber(ownerId, interviewId, previousRoundNo)
                .orElseThrow(() -> new IllegalStateException("MySQL缺少上一面试回合"));
        requirePreviousQuestionScope(ownerId, interviewId, previousRoundNo, previousRound, previousQuestion);

        List<String> completedQuestions = completedQuestionSummaries(questions);
        if (routeDecision == InterviewRouteDecision.FOLLOW_UP) {
            return prepareFollowUp(
                    context,
                    previousRound,
                    previousQuestion,
                    nextRoundNo,
                    completedQuestions
            );
        }

        int completedPlannedQuestions = Math.toIntExact(
                questions.stream().filter(question -> !question.followUp()).count()
        );
        InterviewBlueprint.QuestionPlan plan = context.blueprint()
                .questionAt(completedPlannedQuestions + 1);
        return questionInput(
                context,
                nextRoundNo,
                plan.questionType(),
                plan.difficulty(),
                completedQuestions,
                plan.currentRoundGoal()
        );
    }

    private InterviewQuestionInput prepareFollowUp(
            PlanningContext context,
            InterviewRound previousRound,
            InterviewQuestion previousQuestion,
            int nextRoundNo,
            List<String> completedQuestions
    ) {
        if (!previousQuestion.followUpAllowed()) {
            throw new IllegalStateException("上一问题不允许追问");
        }

        ActorId ownerId = context.session().ownerId();
        UUID interviewId = context.session().interviewId();
        InterviewAnswer answer = roundRepository
                .findAnswerByQuestion(ownerId, interviewId, previousQuestion.questionId())
                .orElseThrow(() -> new IllegalStateException("MySQL缺少上一问题答案"));
        TechnicalReview review = reviewRepository
                .findTechnicalReviewByAnswer(ownerId, interviewId, answer.answerId())
                .orElseThrow(() -> new IllegalStateException("MySQL缺少上一轮技术评审"));

        if (!answer.ownerId().equals(ownerId)
                || !answer.interviewId().equals(interviewId)
                || !answer.roundId().equals(previousRound.roundId())
                || !answer.questionId().equals(previousQuestion.questionId())
                || !review.ownerId().equals(ownerId)
                || !review.interviewId().equals(interviewId)
                || !review.roundId().equals(previousRound.roundId())
                || !review.questionId().equals(previousQuestion.questionId())
                || !review.answerId().equals(answer.answerId())) {
            throw new IllegalStateException("追问依据的答案或技术评审作用域不一致");
        }

        String followUpGoal = boundedText(review.suggestedFollowUp(), 1_000);
        if (followUpGoal.isBlank()) throw new IllegalStateException("技术评审没有提供追问建议");

        return questionInput(
                context,
                nextRoundNo,
                previousQuestion.questionType(),
                previousQuestion.difficulty(),
                completedQuestions,
                followUpGoal
        );
    }

    private InterviewQuestionInput questionInput(
            PlanningContext context,
            int roundNo,
            InterviewQuestionType questionType,
            int difficulty,
            List<String> completedQuestions,
            String currentRoundGoal
    ) {
        return new InterviewQuestionInput(
                context.session().interviewId(),
                roundNo,
                context.session().mode(),
                questionType,
                difficulty,
                blueprintSummary(context.blueprint()),
                targetRoleSummary(context.targetRole().requirementsSnapshot()),
                evidenceByChunkId(context.artifacts()),
                completedQuestions,
                boundedText(currentRoundGoal, 1_000)
        );
    }

    private List<String> completedQuestionSummaries(List<InterviewQuestion> questions) {
        return questions.stream()
                .map(question -> boundedText(question.questionText(), 1_000))
                .toList();
    }

    private String boundedText(String value, int maxCodePoints) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.strip().replaceAll("\\s+", " ");
        int length = normalized.codePointCount(0, normalized.length());
        if (length <= maxCodePoints) return normalized;
        int endIndex = normalized.offsetByCodePoints(0, maxCodePoints - 1);
        return normalized.substring(0, endIndex) + "…";
    }

    private void requirePreviousQuestionScope(
            ActorId ownerId,
            UUID interviewId,
            int roundNo,
            InterviewRound round,
            InterviewQuestion question
    ) {
        if (!round.ownerId().equals(ownerId)
                || !round.interviewId().equals(interviewId)
                || round.roundNo() != roundNo
                || round.status() != InterviewRoundStatus.REVIEWED
                || !question.ownerId().equals(ownerId)
                || !question.interviewId().equals(interviewId)
                || !question.roundId().equals(round.roundId())) {
            throw new IllegalStateException("上一问题、回合或owner作用域不一致");
        }
    }

    private PlanningContext prepare(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        ActorId ownerId = currentActor();
        MockInterviewSession session = sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
        requirePlanningStatus(session);

        MockInterviewInputSnapshot snapshot = snapshotRepository.findById(ownerId, session.inputSnapshotId())
                .orElseThrow(MockInterviewInputConflictException::new);
        validateSnapshot(session, snapshot);

        TargetRole targetRole = loadTargetRole(ownerId, snapshot);
        SkillGapSnapshot skillGap = loadSkillGap(ownerId, session, snapshot);
        validateTrainingPlan(ownerId, snapshot, skillGap);
        List<PersonalEvidenceArtifact> artifacts = loadArtifacts(ownerId, snapshot);
        InterviewBlueprint blueprint = planner.plan(
                session,
                targetSkills(targetRole.requirementsSnapshot()),
                gapSkills(skillGap),
                artifacts.stream().anyMatch(artifact -> artifact.type() == PersonalEvidenceType.PROJECT)
        );
        return new PlanningContext(session, targetRole, artifacts, blueprint);
    }

    private void requirePlanningStatus(MockInterviewSession session) {
        if (session.status() != InterviewStatus.CREATED
                && session.status() != InterviewStatus.GENERATING_QUESTION) {
            throw new IllegalStateException("只有CREATED或GENERATING_QUESTION状态可以生成蓝图");
        }
    }

    private void validateSnapshot(
            MockInterviewSession session,
            MockInterviewInputSnapshot snapshot
    ) {
        if (!session.ownerId().equals(snapshot.ownerId())
                || !session.inputSnapshotId().equals(snapshot.inputSnapshotId())
                || !session.inputSnapshotHash().equals(snapshot.snapshotHash())) {
            throw new MockInterviewInputConflictException();
        }
    }

    private TargetRole loadTargetRole(
            ActorId ownerId,
            MockInterviewInputSnapshot snapshot
    ) {
        TargetRole targetRole = careerRepository
                .findTargetRole(ownerId, snapshot.targetRoleId())
                .orElseThrow(MockInterviewInputConflictException::new);

        if (targetRole.targetRoleVersion() != snapshot.targetRoleVersion()) {
            throw new MockInterviewInputConflictException();
        }
        return targetRole;
    }

    private SkillGapSnapshot loadSkillGap(
            ActorId ownerId,
            MockInterviewSession session,
            MockInterviewInputSnapshot snapshot
    ) {
        if (snapshot.skillGapSnapshotId() == null) {
            if (session.mode() == InterviewMode.GAP_DRILL) {
                throw new MockInterviewInputConflictException();
            }
            return null;
        }

        SkillGapSnapshot skillGap = careerRepository
                .findSkillGapSnapshot(ownerId, snapshot.skillGapSnapshotId())
                .orElseThrow(MockInterviewInputConflictException::new);

        if (!skillGap.targetRoleId().equals(snapshot.targetRoleId())
                || skillGap.targetRoleVersion() != snapshot.targetRoleVersion()) {
            throw new MockInterviewInputConflictException();
        }
        return skillGap;
    }

    private void validateTrainingPlan(
            ActorId ownerId,
            MockInterviewInputSnapshot snapshot,
            SkillGapSnapshot skillGap
    ) {
        if (snapshot.trainingPlanId() == null) return;
        if (skillGap == null) throw new MockInterviewInputConflictException();

        TrainingPlan trainingPlan = careerRepository
                .findTrainingPlan(ownerId, snapshot.trainingPlanId())
                .orElseThrow(MockInterviewInputConflictException::new);

        if (trainingPlan.planVersion() != snapshot.trainingPlanVersion()
                || !trainingPlan.gapSnapshotId().equals(skillGap.snapshotId())) {
            throw new MockInterviewInputConflictException();
        }
    }

    private List<PersonalEvidenceArtifact> loadArtifacts(
            ActorId ownerId,
            MockInterviewInputSnapshot snapshot
    ) {
        List<PersonalEvidenceArtifact> artifacts = new ArrayList<>();

        for (MockInterviewInputSnapshot.ArtifactReference reference : snapshot.artifactReferences()) {
            PersonalEvidenceArtifact artifact = evidenceRepository.findVersionForSnapshot(
                    ownerId,
                    reference.artifactId(),
                    reference.artifactVersion()
            ).orElseThrow(MockInterviewInputConflictException::new);

            if (artifact.status() == PersonalEvidenceStatus.REVOKED
                    || !artifact.sourceHash().equals(reference.artifactSourceHash())) {
                throw new MockInterviewInputConflictException();
            }
            artifacts.add(artifact);
        }
        return List.copyOf(artifacts);
    }

    private List<String> targetSkills(JobRequirements requirements) {
        List<String> candidates = new ArrayList<>();
        candidates.addAll(requirements.interviewTopics());
        candidates.addAll(requirements.programmingLanguages());
        candidates.addAll(requirements.agentRequirements());
        candidates.addAll(requirements.ragRequirements());
        candidates.addAll(requirements.backendAndInfrastructureRequirements());
        candidates.addAll(requirements.engineeringRequirements());

        if (candidates.stream().allMatch(value -> value == null || value.isBlank())) {
            candidates.add(requirements.jobTitle());
        }
        return compactDistinct(candidates);
    }

    private List<String> gapSkills(SkillGapSnapshot skillGap) {
        if (skillGap == null) return List.of();

        return compactDistinct(skillGap.items().stream()
                .filter(item -> item.status() != SkillGapSnapshot.GapStatus.MATCHED)
                .map(SkillGapSnapshot.GapItem::requirementText)
                .toList());
    }

    private List<String> compactDistinct(List<String> values) {
        Map<String, String> distinct = new LinkedHashMap<>();

        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String compact = compact(value);
            distinct.putIfAbsent(compact.toLowerCase(Locale.ROOT), compact);
            if (distinct.size() == MAX_BLUEPRINT_SKILLS) break;
        }

        if (distinct.isEmpty()) {
            throw new MockInterviewInputConflictException();
        }
        return List.copyOf(distinct.values());
    }

    private String compact(String value) {
        String normalized = value.strip().replaceAll("\\s+", " ");
        int length = normalized.codePointCount(0, normalized.length());
        if (length <= MAX_SKILL_LENGTH) return normalized;

        int endIndex = normalized.offsetByCodePoints(0, MAX_SKILL_LENGTH - 1);
        return normalized.substring(0, endIndex) + "…";
    }

    private String blueprintSummary(InterviewBlueprint blueprint) {
        StringBuilder summary = new StringBuilder()
                .append("模式=").append(blueprint.mode())
                .append("；最大问题数=").append(blueprint.budgetPolicy().maxQuestions())
                .append("；问题计划=");

        for (InterviewBlueprint.QuestionPlan plan : blueprint.questionPlans()) {
            if (plan.sequence() > 1) summary.append("；");
            summary.append("第").append(plan.sequence()).append("题=")
                    .append(plan.questionType()).append("/难度").append(plan.difficulty())
                    .append("/技能").append(String.join("、", plan.targetSkills()));
        }
        return summary.toString();
    }

    private String targetRoleSummary(JobRequirements requirements) {
        return "岗位=" + compact(requirements.jobTitle())
                + "；核心要求=" + String.join("、", targetSkills(requirements));
    }

    private Map<String, String> evidenceByChunkId(List<PersonalEvidenceArtifact> artifacts) {
        Map<String, String> evidence = new LinkedHashMap<>();

        for (PersonalEvidenceArtifact artifact : artifacts) {
            for (PersonalEvidenceArtifact.Chunk chunk : artifact.chunks()) {
                evidence.putIfAbsent(chunk.evidenceChunkId(), chunk.chunkContent());
                if (evidence.size() == MAX_QUESTION_EVIDENCE_CHUNKS) {
                    return Collections.unmodifiableMap(evidence);
                }
            }
        }
        return Collections.unmodifiableMap(evidence);
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存一次只读准备过程重建出的冻结事实和确定性蓝图
     * @author: Miao Zheng
     * @date: 2026-08-28
     * @param session 当前面试
     * @param targetRole 冻结目标岗位版本
     * @param artifacts 冻结个人证据版本
     * @param blueprint 确定性面试蓝图
     **/
    private record PlanningContext(
            MockInterviewSession session,
            TargetRole targetRole,
            List<PersonalEvidenceArtifact> artifacts,
            InterviewBlueprint blueprint
    ) {
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}