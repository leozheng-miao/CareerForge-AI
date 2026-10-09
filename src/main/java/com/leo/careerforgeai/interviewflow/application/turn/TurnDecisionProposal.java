package com.leo.careerforgeai.interviewflow.application.turn;

import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.FinalDecision;

import java.util.List;
import java.util.Objects;

/** 模型提供但尚未通过Java策略门的回合决策建议。 */
public record TurnDecisionProposal(
        FinalDecision decision,
        String analysis,
        String question,
        List<String> citedChunkIds,
        boolean searchRequired
) {

    public TurnDecisionProposal {
        Objects.requireNonNull(decision, "decision不能为空");
        if (analysis == null || analysis.isBlank()
                || analysis.length() > 2_000) {
            throw new IllegalArgumentException("analysis不合法");
        }
        citedChunkIds = citedChunkIds == null
                ? List.of()
                : List.copyOf(citedChunkIds);
        if (citedChunkIds.size() > 10
                || citedChunkIds.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("citedChunkIds不合法");
        }
        question = question == null ? null : question.strip();
    }
}
