package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.SourceReference;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 固定快速面试八个考查目标和管理员维护的合格备用主问题
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public final class QuickInterviewPolicyPack {

    public static final String VERSION = "quick-ai-agent-v1";
    public static final int REQUIRED_TARGET_COUNT = 8;

    private final List<TargetPolicy> targets;

    public QuickInterviewPolicyPack() {
        this(defaultTargets());
    }

    QuickInterviewPolicyPack(List<TargetPolicy> targets) {
        Objects.requireNonNull(targets, "targets不能为空");
        this.targets = List.copyOf(targets);
        validateTargets(this.targets);
    }

    public List<TargetPolicy> targets() {
        return targets;
    }

    private static List<TargetPolicy> defaultTargets() {
        return List.of(
                target(
                        "JAVA_BACKEND",
                        "Java后端服务设计、事务边界与并发一致性",
                        "请设计一个由 Java 后端承载的 Agent 业务流程，说明你会如何划分业务服务、事务边界，并处理重复请求与并发更新。"
                ),
                target(
                        "AGENT_WORKFLOW",
                        "确定性工作流与模型动态决策的职责边界",
                        "在一个需要多轮决策的 Agent 应用中，哪些控制逻辑应由 Java 状态机负责，哪些判断可以交给模型？请结合终止条件说明。"
                ),
                target(
                        "TOOL_CALLING",
                        "Tool Calling 的 Schema、权限和执行结果校验",
                        "请设计一个供 Agent 调用的业务工具，说明输入 Schema、owner 权限、超时、幂等和工具结果校验分别放在哪里实现。"
                ),
                target(
                        "RAG",
                        "RAG 检索、重排、来源引用与无结果处理",
                        "请说明一个面向生产业务的 RAG 链路如何完成召回、重排、上下文组装和来源引用，并解释无可靠结果时如何处理。"
                ),
                target(
                        "CONTEXT_STATE",
                        "长会话上下文、状态、摘要和陈旧结果控制",
                        "当 Agent 会话持续较长且后台任务可能延迟返回时，你会如何重建上下文、压缩历史，并阻止陈旧结果覆盖新状态？"
                ),
                target(
                        "RELIABILITY",
                        "模型调用的超时、重试、降级和故障恢复",
                        "请为一次关键模型调用设计可靠性策略，说明哪些错误可以重试、如何限制重试，以及模型持续失败时业务怎样安全降级。"
                ),
                target(
                        "EVALUATION_OBSERVABILITY",
                        "Agent 评测、Tracing、延迟、Token 与成本观测",
                        "你会如何为一个 Agent 功能建立可重复的评测与可观测性体系？请说明数据集、指标、Trace、延迟和 Token 成本口径。"
                ),
                target(
                        "SECURITY",
                        "Prompt Injection、数据隔离和最小工具权限",
                        "面对不可信用户输入和 RAG 文档中的 Prompt Injection，你会如何在 Java 应用层实施数据隔离、最小权限和持久化前校验？"
                )
        );
    }

    private static TargetPolicy target(
            String code,
            String objective,
            String backupQuestion
    ) {
        SourceReference source = new SourceReference(
                "careerforge-admin-policy",
                "quick-interview-backup-" + VERSION,
                sha256(VERSION + ":" + code + ":" + backupQuestion)
        );
        return new TargetPolicy(
                code,
                objective,
                backupQuestion,
                List.of(source)
        );
    }

    private static void validateTargets(List<TargetPolicy> targets) {
        if (targets.size() != REQUIRED_TARGET_COUNT) {
            throw new IllegalArgumentException(
                    "快速面试必须配置8个考查目标"
            );
        }
        Set<String> codes = new HashSet<>();
        Set<String> objectives = new HashSet<>();
        Set<String> questions = new HashSet<>();
        for (TargetPolicy target : targets) {
            Objects.requireNonNull(target, "target不能为空");
            if (!codes.add(target.code())
                    || !objectives.add(target.objective())
                    || !questions.add(target.backupQuestion())) {
                throw new IllegalArgumentException(
                        "快速面试目标和备用题不能重复"
                );
            }
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JVM不支持SHA-256",
                    exception
            );
        }
    }

    public record TargetPolicy(
            String code,
            String objective,
            String backupQuestion,
            List<SourceReference> backupSources
    ) {

        public TargetPolicy {
            code = requireText(code, "code");
            objective = requireText(objective, "objective");
            backupQuestion = requireText(
                    backupQuestion,
                    "backupQuestion"
            );
            if (!code.matches("[A-Z][A-Z0-9_]{1,63}")) {
                throw new IllegalArgumentException("code格式不合法");
            }
            if (backupQuestion.length() < 20
                    || backupQuestion.length() > 2_000) {
                throw new IllegalArgumentException(
                        "备用题长度必须在20到2000字符之间"
                );
            }
            Objects.requireNonNull(backupSources, "backupSources不能为空");
            if (backupSources.isEmpty()
                    || backupSources.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "备用题必须保留管理员题源"
                );
            }
            backupSources = List.copyOf(backupSources);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.strip();
    }
}
