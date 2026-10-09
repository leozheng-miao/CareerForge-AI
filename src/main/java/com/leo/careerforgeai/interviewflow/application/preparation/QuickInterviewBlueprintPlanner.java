package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.domain.topic.InterviewTopic;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 以确定性规则为每场快速面试生成八个有序考查目标
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Component
public final class QuickInterviewBlueprintPlanner {

    private final QuickInterviewPolicyPack policyPack;

    public QuickInterviewBlueprintPlanner() {
        this(new QuickInterviewPolicyPack());
    }

    QuickInterviewBlueprintPlanner(QuickInterviewPolicyPack policyPack) {
        this.policyPack = Objects.requireNonNull(
                policyPack,
                "policyPack不能为空"
        );
    }

    public QuickInterviewBlueprint plan(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        List<PlannedTarget> targets = policyPack.targets().stream()
                .map(target -> plannedTarget(interviewId, target))
                .toList();
        validate(targets);
        return new QuickInterviewBlueprint(
                QuickInterviewPolicyPack.VERSION,
                targets
        );
    }

    private static PlannedTarget plannedTarget(
            UUID interviewId,
            QuickInterviewPolicyPack.TargetPolicy target
    ) {
        int sequenceNo = sequenceOf(target.code());
        UUID topicId = UUID.nameUUIDFromBytes((
                "quick-topic:" + interviewId + ":" + target.code()
        ).getBytes(StandardCharsets.UTF_8));
        InterviewTopic topic = InterviewTopic.planned(
                topicId,
                sequenceNo,
                target.objective()
        );
        return new PlannedTarget(topic, target);
    }

    private static int sequenceOf(String code) {
        List<String> orderedCodes = List.of(
                "JAVA_BACKEND",
                "AGENT_WORKFLOW",
                "TOOL_CALLING",
                "RAG",
                "CONTEXT_STATE",
                "RELIABILITY",
                "EVALUATION_OBSERVABILITY",
                "SECURITY"
        );
        int index = orderedCodes.indexOf(code);
        if (index < 0) {
            throw new IllegalArgumentException("未知快速面试目标: " + code);
        }
        return index + 1;
    }

    private static void validate(List<PlannedTarget> targets) {
        if (targets.size() != QuickInterviewPolicyPack.REQUIRED_TARGET_COUNT) {
            throw new IllegalStateException("快速面试蓝图必须包含8个目标");
        }
        Set<UUID> topicIds = new HashSet<>();
        Set<String> objectives = new HashSet<>();
        for (int index = 0; index < targets.size(); index++) {
            InterviewTopic topic = targets.get(index).topic();
            if (topic.sequenceNo() != index + 1
                    || !topicIds.add(topic.topicId())
                    || !objectives.add(topic.objective())) {
                throw new IllegalStateException(
                        "快速面试蓝图目标必须有序且互不重复"
                );
            }
        }
    }

    public record QuickInterviewBlueprint(
            String policyVersion,
            List<PlannedTarget> targets
    ) {

        public QuickInterviewBlueprint {
            if (policyVersion == null || policyVersion.isBlank()) {
                throw new IllegalArgumentException("policyVersion不能为空");
            }
            Objects.requireNonNull(targets, "targets不能为空");
            targets = List.copyOf(targets);
        }

        public List<InterviewTopic> topics() {
            return targets.stream().map(PlannedTarget::topic).toList();
        }
    }

    public record PlannedTarget(
            InterviewTopic topic,
            QuickInterviewPolicyPack.TargetPolicy policy
    ) {

        public PlannedTarget {
            Objects.requireNonNull(topic, "topic不能为空");
            Objects.requireNonNull(policy, "policy不能为空");
        }
    }
}
