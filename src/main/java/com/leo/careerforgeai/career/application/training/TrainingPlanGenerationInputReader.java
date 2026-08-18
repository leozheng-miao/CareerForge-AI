package com.leo.careerforgeai.career.application.training;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.skillgap.DeterministicSkillGapMatcher;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import com.leo.careerforgeai.knowledge.infrastructure.document.loading.DocumentLoadException;
import com.leo.careerforgeai.knowledge.infrastructure.document.loading.MarkdownDocumentLoader;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.application.profile.MemoryProfileQueryApplicationService;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 为训练计划生成读取并复核固定版本的岗位、Gap、画像、Memory和受控资源输入
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
@Service
@ConditionalOnBean(CareerPlanningRepository.class)
public class TrainingPlanGenerationInputReader {

    public static final String INPUT_POLICY_VERSION = "training-plan-input-v1";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern HOURS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:小时|hours?|hrs?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEEK_MARKER_PATTERN = Pattern.compile("每周|weekly|per\\s+week|/\\s*week", Pattern.CASE_INSENSITIVE);

    private final CurrentActorProvider currentActorProvider;
    private final CareerPlanningRepository repository;
    private final MemoryProfileQueryApplicationService profileQueryService;
    private final MarkdownDocumentLoader documentLoader;

    public TrainingPlanGenerationInputReader(
            CurrentActorProvider currentActorProvider,
            CareerPlanningRepository repository,
            MemoryProfileQueryApplicationService profileQueryService,
            MarkdownDocumentLoader documentLoader
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.profileQueryService = Objects.requireNonNull(profileQueryService, "profileQueryService不能为空");
        this.documentLoader = Objects.requireNonNull(documentLoader, "documentLoader不能为空");
    }

    public FixedInput read(UUID snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId不能为空");
        ActorId actorId = Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");

        SkillGapSnapshot snapshot = repository.findSkillGapSnapshot(actorId, snapshotId)
                .orElseThrow(() -> failure(ErrorType.GAP_SNAPSHOT_NOT_FOUND, "能力差距快照不存在或不属于当前用户"));
        requireOwner(actorId, snapshot.ownerId(), "Gap快照owner边界异常");

        TargetRole targetRole = repository.findTargetRole(actorId, snapshot.targetRoleId())
                .orElseThrow(() -> failure(ErrorType.INPUT_INTEGRITY_VIOLATION, "Gap快照关联的TargetRole不存在"));
        validateTargetRole(actorId, snapshot, targetRole);
        validateLatestTargetRole(actorId, targetRole);

        ConfirmedSkillProfile skillProfile = profileQueryService.findConfirmedSkillProfile();
        requireOwner(actorId, skillProfile.ownerId(), "技能画像owner边界异常");
        if (skillProfile.profileVersion() != snapshot.profileVersion()) {
            throw failure(ErrorType.INPUT_VERSION_CONFLICT, "技能画像版本已经变化，请重新生成Gap快照");
        }
        if (!DeterministicSkillGapMatcher.ALGORITHM_VERSION.equals(snapshot.algorithmVersion())) {
            throw failure(ErrorType.INPUT_VERSION_CONFLICT, "Gap算法版本已经变化，请重新生成Gap快照");
        }

        List<MemoryItem> planningMemories = validatePlanningMemories(
                actorId,
                profileQueryService.findConfirmedPlanningMemories()
        );
        int weeklyAvailableMinutes = resolveWeeklyAvailableMinutes(planningMemories);
        List<ControlledResource> controlledResources = loadControlledResources();

        return new FixedInput(
                INPUT_POLICY_VERSION,
                actorId,
                targetRole,
                snapshot,
                skillProfile,
                weeklyAvailableMinutes,
                planningMemories,
                controlledResources
        );
    }

    private void validateTargetRole(ActorId actorId, SkillGapSnapshot snapshot, TargetRole targetRole) {
        requireOwner(actorId, targetRole.ownerId(), "TargetRole owner边界异常");
        if (!targetRole.targetRoleId().equals(snapshot.targetRoleId())
                || targetRole.targetRoleVersion() != snapshot.targetRoleVersion()) {
            throw failure(ErrorType.INPUT_INTEGRITY_VIOLATION, "Gap快照与TargetRole版本不一致");
        }
    }

    private void validateLatestTargetRole(ActorId actorId, TargetRole selectedTargetRole) {
        TargetRole latest = repository.findLatestTargetRole(actorId)
                .orElseThrow(() -> failure(ErrorType.INPUT_INTEGRITY_VIOLATION, "当前用户不存在有效TargetRole"));
        requireOwner(actorId, latest.ownerId(), "最新TargetRole owner边界异常");
        if (!latest.targetRoleId().equals(selectedTargetRole.targetRoleId())
                || latest.targetRoleVersion() != selectedTargetRole.targetRoleVersion()) {
            throw failure(ErrorType.INPUT_VERSION_CONFLICT, "目标岗位版本已经变化，请重新生成Gap快照");
        }
    }

    private List<MemoryItem> validatePlanningMemories(ActorId actorId, List<MemoryItem> memories) {
        if (memories == null) {
            throw failure(ErrorType.INPUT_INTEGRITY_VIOLATION, "计划约束查询结果不能为空");
        }

        Set<String> occupiedSlots = new HashSet<>();
        List<MemoryItem> validated = new ArrayList<>(memories.size());

        for (MemoryItem memory : memories) {
            if (memory == null
                    || !actorId.equals(memory.ownerId())
                    || memory.status() != MemoryStatus.CONFIRMED
                    || (memory.type() != MemoryType.TIME_CONSTRAINT
                    && memory.type() != MemoryType.LEARNING_PREFERENCE)) {
                throw failure(ErrorType.INPUT_INTEGRITY_VIOLATION, "计划约束违反owner、状态或类型边界");
            }

            String slot = memory.type().name() + ":" + memory.normalizedKey().value();
            if (!occupiedSlots.add(slot)) {
                throw failure(ErrorType.INPUT_INTEGRITY_VIOLATION, "存在重复的已确认计划约束槽位");
            }
            validated.add(memory);
        }

        validated.sort(
                Comparator.comparing((MemoryItem memory) -> memory.type().name())
                        .thenComparing(memory -> memory.normalizedKey().value())
                        .thenComparing(MemoryItem::memoryId)
        );
        return List.copyOf(validated);
    }

    private int resolveWeeklyAvailableMinutes(List<MemoryItem> planningMemories) {
        List<MemoryItem> weeklyConstraints = planningMemories.stream()
                .filter(memory -> memory.type() == MemoryType.TIME_CONSTRAINT
                        && memory.normalizedKey().value().equals(TimeConstraintKey.WEEKLY_HOURS.value()))
                .toList();

        if (weeklyConstraints.isEmpty()) {
            throw failure(ErrorType.TIME_CONSTRAINT_MISSING, "缺少已确认的每周可用学习时间");
        }
        if (weeklyConstraints.size() != 1) {
            throw failure(ErrorType.TIME_CONSTRAINT_INVALID, "每周可用学习时间存在冲突");
        }

        String content = weeklyConstraints.getFirst().content();
        if (!WEEK_MARKER_PATTERN.matcher(content).find()) {
            throw failure(ErrorType.TIME_CONSTRAINT_INVALID, "每周可用学习时间缺少明确的每周语义");
        }

        List<String> allNumbers = NUMBER_PATTERN.matcher(content)
                .results()
                .map(result -> result.group())
                .toList();
        Matcher hoursMatcher = HOURS_PATTERN.matcher(content);

        if (allNumbers.size() != 1 || !hoursMatcher.find()) {
            throw failure(ErrorType.TIME_CONSTRAINT_INVALID, "每周可用学习时间无法唯一解析");
        }

        String hoursText = hoursMatcher.group(1);
        if (hoursMatcher.find() || !hoursText.equals(allNumbers.getFirst())) {
            throw failure(ErrorType.TIME_CONSTRAINT_INVALID, "每周可用学习时间包含多个候选值");
        }

        try {
            int minutes = new BigDecimal(hoursText).multiply(BigDecimal.valueOf(60)).intValueExact();
            if (minutes < 1 || minutes > TrainingPlanItem.MAX_ESTIMATED_MINUTES) {
                throw new ArithmeticException("分钟数超出范围");
            }
            return minutes;
        } catch (ArithmeticException exception) {
            throw failure(ErrorType.TIME_CONSTRAINT_INVALID, "每周可用学习时间超出允许范围", exception);
        }
    }

    private List<ControlledResource> loadControlledResources() {
        List<SourceDocument> documents;
        try {
            documents = documentLoader.loadAll();
        } catch (DocumentLoadException exception) {
            throw failure(ErrorType.CONTROLLED_RESOURCE_INVALID, "受控知识资源加载失败", exception);
        }

        if (documents == null || documents.isEmpty()) {
            throw failure(ErrorType.CONTROLLED_RESOURCE_INVALID, "受控知识资源不能为空");
        }

        Set<String> documentIds = new HashSet<>();
        List<ControlledResource> resources = new ArrayList<>(documents.size());

        for (SourceDocument document : documents) {
            if (document == null) {
                throw failure(ErrorType.CONTROLLED_RESOURCE_INVALID, "受控知识资源不能包含空值");
            }
            if (!documentIds.add(document.documentId())) {
                throw failure(ErrorType.CONTROLLED_RESOURCE_INVALID, "受控知识资源ID重复");
            }
            resources.add(ControlledResource.from(document));
        }

        resources.sort(
                Comparator.comparing(ControlledResource::knowledgeBaseId)
                        .thenComparing(ControlledResource::documentId)
        );
        return List.copyOf(resources);
    }

    private static void requireOwner(ActorId expected, ActorId actual, String message) {
        if (!expected.equals(actual)) {
            throw failure(ErrorType.INPUT_INTEGRITY_VIOLATION, message);
        }
    }

    private static TrainingPlanGenerationException failure(ErrorType errorType, String message) {
        return new TrainingPlanGenerationException(errorType, message);
    }

    private static TrainingPlanGenerationException failure(
            ErrorType errorType,
            String message,
            Throwable cause
    ) {
        return new TrainingPlanGenerationException(errorType, message, cause);
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存一次训练计划生成使用的固定可信输入
     * @author: Miao Zheng
     * @date: 2026-08-18
     * @param inputPolicyVersion 输入读取与校验策略版本
     * @param ownerId 输入所属用户
     * @param targetRole 固定目标岗位版本
     * @param gapSnapshot 固定能力差距快照
     * @param skillProfile 固定技能画像版本及当前证据
     * @param weeklyAvailableMinutes 每周可用学习分钟数
     * @param planningMemories 已确认时间约束与学习偏好
     * @param controlledResources 本次模型可引用的资源白名单
     */
    public record FixedInput(
            String inputPolicyVersion,
            ActorId ownerId,
            TargetRole targetRole,
            SkillGapSnapshot gapSnapshot,
            ConfirmedSkillProfile skillProfile,
            int weeklyAvailableMinutes,
            List<MemoryItem> planningMemories,
            List<ControlledResource> controlledResources
    ) {
        public FixedInput {
            if (inputPolicyVersion == null || inputPolicyVersion.isBlank()) {
                throw new IllegalArgumentException("inputPolicyVersion不能为空");
            }
            Objects.requireNonNull(ownerId, "ownerId不能为空");
            Objects.requireNonNull(targetRole, "targetRole不能为空");
            Objects.requireNonNull(gapSnapshot, "gapSnapshot不能为空");
            Objects.requireNonNull(skillProfile, "skillProfile不能为空");
            if (weeklyAvailableMinutes < 1) {
                throw new IllegalArgumentException("weeklyAvailableMinutes必须大于0");
            }

            inputPolicyVersion = inputPolicyVersion.strip();
            planningMemories = List.copyOf(
                    Objects.requireNonNull(planningMemories, "planningMemories不能为空")
            );
            controlledResources = List.copyOf(
                    Objects.requireNonNull(controlledResources, "controlledResources不能为空")
            );

            if (planningMemories.isEmpty()) {
                throw new IllegalArgumentException("planningMemories不能为空");
            }
            if (controlledResources.isEmpty()) {
                throw new IllegalArgumentException("controlledResources不能为空");
            }
        }

        public List<TrainingPlan.MemoryInputRef> memoryRefs() {
            return planningMemories.stream()
                    .map(memory -> new TrainingPlan.MemoryInputRef(
                            memory.memoryId(),
                            memory.version(),
                            memory.type(),
                            memory.normalizedKey().value(),
                            memory.contentHash()
                    ))
                    .toList();
        }

        public List<TrainingPlan.ResourceInputRef> resourceRefs() {
            return controlledResources.stream()
                    .map(resource -> new TrainingPlan.ResourceInputRef(
                            TrainingPlanItem.ResourceType.KNOWLEDGE_DOCUMENT,
                            resource.documentId(),
                            resource.sourceHash()
                    ))
                    .toList();
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存提供给模型引用的受控知识资源元数据
     * @author: Miao Zheng
     * @date: 2026-08-18
     * @param knowledgeBaseId 知识库ID
     * @param documentId 文档资源ID
     * @param documentName 文档名称
     * @param documentType 文档业务类型
     * @param sourceHash 文档内容版本哈希
     */
    public record ControlledResource(
            String knowledgeBaseId,
            String documentId,
            String documentName,
            KnowledgeDocumentType documentType,
            String sourceHash
    ) {
        public ControlledResource {
            knowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId");
            documentId = requireText(documentId, "documentId");
            documentName = requireText(documentName, "documentName");
            Objects.requireNonNull(documentType, "documentType不能为空");
            if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("sourceHash必须是小写SHA-256");
            }
        }

        private static ControlledResource from(SourceDocument document) {
            return new ControlledResource(
                    document.knowledgeBaseId(),
                    document.documentId(),
                    document.documentName(),
                    document.documentType(),
                    document.sourceHash()
            );
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + "不能为空");
            }
            return value.strip();
        }
    }
}