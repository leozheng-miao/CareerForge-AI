package com.leo.careerforgeai.interview.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 聚合待用户确认的面试复盘报告及可执行Memory和训练计划建议
 * @author: Miao Zheng
 * @date: 2026-08-29
 * @param reportId 报告UUID
 * @param interviewId 所属面试UUID
 * @param ownerId 所属用户
 * @param reportVersion 报告业务版本
 * @param status 报告确认状态
 * @param strengths 面试事实支持的优势
 * @param technicalGaps 技术能力差距
 * @param evidenceExpressionRisks 证据表达或一致性风险
 * @param improvementActions 可执行改进动作
 * @param suggestions 待用户逐项决定的结构化建议
 * @param modelRequestId 报告模型请求ID
 * @param promptVersion Prompt版本
 * @param inputHash 报告输入的小写SHA-256
 * @param outputHash 模型结构化输出的小写SHA-256
 * @param version 聚合乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 * @param decidedAt 完成用户决定的时间
 */
public record InterviewReport(
        UUID reportId,
        UUID interviewId,
        ActorId ownerId,
        long reportVersion,
        Status status,
        List<String> strengths,
        List<String> technicalGaps,
        List<String> evidenceExpressionRisks,
        List<String> improvementActions,
        List<Suggestion> suggestions,
        String modelRequestId,
        String promptVersion,
        String inputHash,
        String outputHash,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant decidedAt
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public InterviewReport {
        Objects.requireNonNull(reportId, "reportId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");
        if (reportVersion < 1) throw new IllegalArgumentException("reportVersion必须从1开始");
        if (version < 0) throw new IllegalArgumentException("version不能小于0");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt不能早于createdAt");

        strengths = requireItems(strengths, "strengths", 0, 20);
        technicalGaps = requireItems(technicalGaps, "technicalGaps", 0, 20);
        evidenceExpressionRisks = requireItems(evidenceExpressionRisks, "evidenceExpressionRisks", 0, 20);
        improvementActions = requireItems(improvementActions, "improvementActions", 1, 20);
        suggestions = requireSuggestions(suggestions, reportId, interviewId, ownerId);

        requireText(modelRequestId, "modelRequestId", 128);
        requireText(promptVersion, "promptVersion", 64);
        inputHash = requireSha256(inputHash, "inputHash");
        outputHash = requireSha256(outputHash, "outputHash");

        if ((status == Status.DECIDED) != (decidedAt != null)) {
            throw new IllegalArgumentException("decidedAt与报告状态不匹配");
        }
        if (decidedAt != null && decidedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("decidedAt不能早于createdAt");
        }
    }

    public static InterviewReport pendingConfirmation(
            UUID reportId,
            UUID interviewId,
            ActorId ownerId,
            List<String> strengths,
            List<String> technicalGaps,
            List<String> evidenceExpressionRisks,
            List<String> improvementActions,
            List<Suggestion> suggestions,
            String modelRequestId,
            String promptVersion,
            String inputHash,
            String outputHash,
            Instant now
    ) {
        Objects.requireNonNull(now, "now不能为空");
        return new InterviewReport(
                reportId, interviewId, ownerId, 1, Status.PENDING_CONFIRMATION,
                strengths, technicalGaps, evidenceExpressionRisks, improvementActions,
                suggestions, modelRequestId, promptVersion, inputHash, outputHash,
                0, now, now, null
        );
    }

    public InterviewReport decide(Instant now) {
        Objects.requireNonNull(now, "now不能为空");
        if (status == Status.DECIDED) return this;
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("决定时间不能早于报告更新时间");
        return new InterviewReport(
                reportId, interviewId, ownerId, reportVersion, Status.DECIDED,
                strengths, technicalGaps, evidenceExpressionRisks, improvementActions,
                suggestions, modelRequestId, promptVersion, inputHash, outputHash,
                nextVersion(), createdAt, now, now
        );
    }

    private long nextVersion() {
        try {
            return Math.incrementExact(version);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("报告版本超出允许范围", exception);
        }
    }

    private static List<String> requireItems(
            List<String> values,
            String fieldName,
            int minSize,
            int maxSize
    ) {
        if (values == null || values.size() < minSize || values.size() > maxSize) {
            throw new IllegalArgumentException(fieldName + "数量必须在" + minSize + "到" + maxSize + "之间");
        }
        List<String> copy = List.copyOf(values);
        for (String value : copy) requireText(value, fieldName + "元素", 1_000);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException(fieldName + "不能包含重复内容");
        }
        return copy;
    }

    private static List<Suggestion> requireSuggestions(
            List<Suggestion> suggestions,
            UUID reportId,
            UUID interviewId,
            ActorId ownerId
    ) {
        if (suggestions == null || suggestions.size() > 20) {
            throw new IllegalArgumentException("suggestions数量不能超过20");
        }

        List<Suggestion> copy = List.copyOf(suggestions);
        Set<String> orderKeys = new HashSet<>();
        Set<String> contentKeys = new HashSet<>();

        for (Suggestion suggestion : copy) {
            Objects.requireNonNull(suggestion, "suggestion不能为空");
            if (!suggestion.reportId().equals(reportId)
                    || !suggestion.interviewId().equals(interviewId)
                    || !suggestion.ownerId().equals(ownerId)) {
                throw new IllegalArgumentException("suggestion与报告作用域不一致");
            }
            if (!orderKeys.add(suggestion.type() + ":" + suggestion.order())) {
                throw new IllegalArgumentException("同类型suggestionOrder不能重复");
            }
            if (!contentKeys.add(suggestion.type() + ":" + suggestion.contentHash())) {
                throw new IllegalArgumentException("同类型建议内容不能重复");
            }
        }

        for (SuggestionType type : SuggestionType.values()) {
            List<Integer> orders = copy.stream()
                    .filter(suggestion -> suggestion.type() == type)
                    .map(Suggestion::order)
                    .sorted()
                    .toList();
            for (int index = 0; index < orders.size(); index++) {
                if (orders.get(index) != index + 1) {
                    throw new IllegalArgumentException(type + "建议顺序必须从1连续递增");
                }
            }
        }
        return copy;
    }

    private static void requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能为空且长度不能超过" + maxLength);
        }
    }

    private static String normalizeText(String value, String fieldName, int maxLength) {
        requireText(value, fieldName, maxLength);
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength);
        }
        return normalized;
    }

    private static String requireSha256(String value, String fieldName) {
        if (value == null || !SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "必须是64位小写SHA-256");
        }
        return value;
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义报告是否仍等待用户决定
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    public enum Status {
        PENDING_CONFIRMATION,
        DECIDED
    }

    /**
     * @program: CareerForge-AI
     * @description: 区分长期Memory候选和训练计划调整建议
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    public enum SuggestionType {
        MEMORY_CANDIDATE,
        TRAINING_PLAN_ADJUSTMENT
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义报告建议携带的受控可执行payload
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    public sealed interface SuggestionPayload
            permits MemoryCandidatePayload, TrainingPlanAdjustmentPayload, LegacyPayload {

        String displayContent();
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义只允许创建SKILL_EVIDENCE Memory候选的payload
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param skillName 用于生成MemoryNormalizedKey的技能名称
     * @param content Memory候选正文
     */
    public record MemoryCandidatePayload(
            String skillName,
            String content
    ) implements SuggestionPayload {

        public MemoryCandidatePayload {
            skillName = normalizeText(skillName, "skillName", 128);
            content = normalizeText(content, "content", 1_000);
        }

        @Override
        public String displayContent() {
            return content;
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义下一版训练计划生成时必须考虑的调整payload
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param focusArea 调整涉及的技能或训练主题
     * @param adjustment 具体调整要求
     */
    public record TrainingPlanAdjustmentPayload(
            String focusArea,
            String adjustment
    ) implements SuggestionPayload {

        public TrainingPlanAdjustmentPayload {
            focusArea = normalizeText(focusArea, "focusArea", 128);
            adjustment = normalizeText(adjustment, "adjustment", 1_000);
        }

        @Override
        public String displayContent() {
            return adjustment;
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 兼容V16以前已存在但不可执行的字符串建议
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param content 旧版展示内容
     */
    public record LegacyPayload(String content) implements SuggestionPayload {

        public LegacyPayload {
            content = normalizeText(content, "content", 1_000);
        }

        @Override
        public String displayContent() {
            return content;
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存报告中等待用户逐项确认的有序结构化建议
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param suggestionId 建议UUID
     * @param reportId 所属报告UUID
     * @param interviewId 所属面试UUID
     * @param ownerId 所属用户
     * @param type 建议类型
     * @param order 同类型建议的顺序
     * @param content 用户可见的建议摘要
     * @param payload 受控结构化建议数据
     * @param contentHash payload或旧版内容的小写SHA-256
     * @param createdAt 创建时间
     */
    public record Suggestion(
            UUID suggestionId,
            UUID reportId,
            UUID interviewId,
            ActorId ownerId,
            SuggestionType type,
            int order,
            String content,
            SuggestionPayload payload,
            String contentHash,
            Instant createdAt
    ) {

        public Suggestion {
            Objects.requireNonNull(suggestionId, "suggestionId不能为空");
            Objects.requireNonNull(reportId, "reportId不能为空");
            Objects.requireNonNull(interviewId, "interviewId不能为空");
            Objects.requireNonNull(ownerId, "ownerId不能为空");
            Objects.requireNonNull(type, "type不能为空");
            Objects.requireNonNull(payload, "payload不能为空");
            Objects.requireNonNull(createdAt, "createdAt不能为空");
            if (order < 1 || order > 10) throw new IllegalArgumentException("order必须在1到10之间");
            content = normalizeText(content, "content", 1_000);
            contentHash = requireSha256(contentHash, "contentHash");
            if (!content.equals(payload.displayContent())) {
                throw new IllegalArgumentException("content与payload展示内容不一致");
            }
            if (payload instanceof MemoryCandidatePayload && type != SuggestionType.MEMORY_CANDIDATE) {
                throw new IllegalArgumentException("MemoryCandidatePayload与建议类型不匹配");
            }
            if (payload instanceof TrainingPlanAdjustmentPayload
                    && type != SuggestionType.TRAINING_PLAN_ADJUSTMENT) {
                throw new IllegalArgumentException("TrainingPlanAdjustmentPayload与建议类型不匹配");
            }
        }
    }
}