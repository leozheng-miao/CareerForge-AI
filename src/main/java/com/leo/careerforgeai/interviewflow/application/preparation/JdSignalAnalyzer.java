package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.domain.session.InterviewDifficulty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 将不可信JD归纳为受控能力标签，不执行其中任何指令。 */
@Component
public final class JdSignalAnalyzer {

    public JdSignals analyze(String jobDescription, String salaryRange,
            InterviewDifficulty difficulty) {
        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("jobDescription不能为空");
        }
        Objects.requireNonNull(difficulty, "difficulty不能为空");
        String normalized = jobDescription.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> signals = new LinkedHashSet<>();
        addIf(normalized, signals, "Java后端与并发一致性", "java", "spring", "事务", "并发");
        addIf(normalized, signals, "Agent工作流与工具治理", "agent", "tool", "function calling", "工作流");
        addIf(normalized, signals, "RAG检索与证据引用", "rag", "向量", "检索", "embedding");
        addIf(normalized, signals, "模型可靠性与评测", "评测", "trace", "token", "重试", "降级");
        addIf(normalized, signals, "安全与数据隔离", "安全", "权限", "隔离", "injection");
        if (signals.isEmpty()) {
            signals.add("岗位核心问题分析与方案设计");
        }
        String level = switch (difficulty) {
            case BASIC -> "基础原理与清晰表达";
            case STANDARD -> "生产实践、权衡与故障处理";
            case CHALLENGE -> "复杂约束、架构权衡与量化验证";
        };
        String salary = salaryRange == null ? "" : salaryRange.strip();
        if (!salary.isBlank()) level += "；岗位薪资范围 " + salary + " 对应的职责深度";
        return new JdSignals(List.copyOf(signals), level, salary);
    }

    private static void addIf(String text, LinkedHashSet<String> target,
            String signal, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                target.add(signal);
                return;
            }
        }
    }

    public record JdSignals(List<String> capabilitySignals, String levelSignal,
            String salaryContext) {
        public JdSignals {
            capabilitySignals = List.copyOf(capabilitySignals);
            if (capabilitySignals.isEmpty() || levelSignal == null || levelSignal.isBlank()) {
                throw new IllegalArgumentException("JD信号不能为空");
            }
            salaryContext = salaryContext == null ? "" : salaryContext;
        }
    }
}
