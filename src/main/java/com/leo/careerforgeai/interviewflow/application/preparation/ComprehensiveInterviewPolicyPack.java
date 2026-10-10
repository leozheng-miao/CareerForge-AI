package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.SourceReference;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 综合面试的平台兜底能力域和管理员来源。 */
public final class ComprehensiveInterviewPolicyPack {
    public static final String VERSION = "comprehensive-ai-agent-v1";
    public static final int REQUIRED_THEME_COUNT = 6;

    public List<CoreTheme> coreThemes() {
        return List.of(
                theme("JOB_CORE", "岗位核心能力与问题拆解"),
                theme("JAVA_ARCHITECTURE", "Java业务架构、事务与一致性"),
                theme("AGENT_BOUNDARY", "Agent与确定性工作流的职责边界"),
                theme("RAG_EVIDENCE", "RAG检索质量、证据与权限隔离"),
                theme("RELIABILITY", "模型可靠性、降级和故障恢复"),
                theme("EVALUATION", "评测、可观测性与成本权衡")
        );
    }

    private static CoreTheme theme(String code, String objective) {
        String question = "围绕“" + objective
                + "”，请结合一个具体场景说明你的方案、关键权衡、失败处理和验证方法。";
        return new CoreTheme(code, objective, question,
                List.of(new SourceReference("careerforge-admin-policy",
                        "comprehensive-backup-" + VERSION,
                        sha256(VERSION + ":" + code + ":" + question))));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record CoreTheme(String code, String objective, String backupQuestion,
            List<SourceReference> sources) {
        public CoreTheme {
            if (code == null || code.isBlank() || objective == null || objective.isBlank()
                    || backupQuestion == null || backupQuestion.length() < 20) {
                throw new IllegalArgumentException("综合面试策略项不合法");
            }
            sources = List.copyOf(sources);
        }
    }
}
