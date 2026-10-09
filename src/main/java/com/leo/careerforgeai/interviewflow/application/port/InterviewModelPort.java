package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.KnowledgeQuestionCandidate;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.SourceReference;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @program: CareerForge-AI
 * @description: 限制准备阶段模型只能生成带来源的受控题目变体
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public interface InterviewModelPort {

    Optional<GeneratedQuestion> generateQuestion(
            QuestionGenerationRequest request
    );

    record QuestionGenerationRequest(
            String direction,
            String objective,
            List<KnowledgeQuestionCandidate> candidates
    ) {

        public QuestionGenerationRequest {
            direction = requireText(direction, "direction");
            objective = requireText(objective, "objective");
            Objects.requireNonNull(candidates, "candidates不能为空");
            if (candidates.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "candidates不能包含null"
                );
            }
            candidates = List.copyOf(candidates);
        }
    }

    record GeneratedQuestion(
            String content,
            List<SourceReference> sourceReferences
    ) {

        public GeneratedQuestion {
            content = requireText(content, "content");
            Objects.requireNonNull(
                    sourceReferences,
                    "sourceReferences不能为空"
            );
            if (sourceReferences.isEmpty()
                    || sourceReferences.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "生成题目必须保留合法来源"
                );
            }
            sourceReferences = List.copyOf(sourceReferences);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.strip();
    }
}
