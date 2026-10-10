package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository.InputSnapshot;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.KnowledgeQuestionCandidate;
import com.leo.careerforgeai.interviewflow.config.InterviewPreparationExecutor;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewDifficulty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

/** 并行收集三类只读信号；失败按信号类型做保守降级。 */
@Component
public final class ComprehensivePreparationCoordinator {
    private static final int KNOWLEDGE_LIMIT = 12;
    private final JdSignalAnalyzer jdAnalyzer;
    private final ProfileEvidenceAnalyzer profileAnalyzer;
    private final InterviewQuestionKnowledgePort knowledge;
    private final InterviewPreparationExecutor executor;

    public ComprehensivePreparationCoordinator(JdSignalAnalyzer jdAnalyzer,
            ProfileEvidenceAnalyzer profileAnalyzer,
            InterviewQuestionKnowledgePort knowledge,
            InterviewPreparationExecutor executor) {
        this.jdAnalyzer = Objects.requireNonNull(jdAnalyzer);
        this.profileAnalyzer = Objects.requireNonNull(profileAnalyzer);
        this.knowledge = Objects.requireNonNull(knowledge);
        this.executor = Objects.requireNonNull(executor);
    }

    public PreparationSignals collect(InputSnapshot input) {
        Objects.requireNonNull(input, "input不能为空");
        Future<JdSignalAnalyzer.JdSignals> jd = executor.submit(() -> jdAnalyzer.analyze(
                input.jobDescription(), input.salaryRange(),
                InterviewDifficulty.valueOf(input.difficulty())));
        Future<ProfileEvidenceAnalyzer.ProfileSignals> profile = executor.submit(
                () -> profileAnalyzer.analyze(input.confirmedProfileJson()));
        Future<List<KnowledgeQuestionCandidate>> candidates = executor.submit(() ->
                knowledge.search(input.direction(), boundedQuery(input.jobDescription()),
                        KNOWLEDGE_LIMIT));
        return new PreparationSignals(required(jd, "JD信号分析失败"),
                optional(profile, ProfileEvidenceAnalyzer.ProfileSignals.empty()),
                optional(candidates, List.of()));
    }

    private static String boundedQuery(String value) {
        String normalized = value.strip();
        return normalized.length() <= 2_000 ? normalized : normalized.substring(0, 2_000);
    }

    private static <T> T required(Future<T> future, String message) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, exception);
        } catch (Exception exception) {
            throw new IllegalStateException(message, exception);
        }
    }

    private static <T> T optional(Future<T> future, T fallback) {
        try {
            T value = future.get();
            return value == null ? fallback : value;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (Exception exception) {
            return fallback;
        }
    }

    public record PreparationSignals(JdSignalAnalyzer.JdSignals jd,
            ProfileEvidenceAnalyzer.ProfileSignals profile,
            List<KnowledgeQuestionCandidate> knowledgeCandidates) {
        public PreparationSignals {
            Objects.requireNonNull(jd);
            Objects.requireNonNull(profile);
            knowledgeCandidates = knowledgeCandidates == null
                    ? List.of() : List.copyOf(knowledgeCandidates);
        }
    }
}
