package com.leo.careerforgeai.knowledge.domain.answer;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 保存经过结构校验和引用验证后的最终 RAG 回答
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public record RagAnswer(
        RagAnswerStatus status,
        String answer,
        List<RagCitation> citations
) {

    public static final String INSUFFICIENT_CONTEXT_MESSAGE = "无法根据当前知识库确认。";

    public RagAnswer {
        if (status == null) throw new IllegalArgumentException("status 不能为空");
        if (answer == null || answer.isBlank()) throw new IllegalArgumentException("answer 不能为空");
        if (citations == null) throw new IllegalArgumentException("citations 不能为空");
        if (citations.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("citations 不能包含 null");
        citations = List.copyOf(citations);

        Set<String> chunkIds = new HashSet<>();
        citations.forEach(citation -> {
            if (!chunkIds.add(citation.chunkId())) throw new IllegalArgumentException("citations 包含重复 Chunk ID");
        });

        if (status == RagAnswerStatus.ANSWERED && citations.isEmpty()) throw new IllegalArgumentException("已回答结果必须包含引用");
        if (status == RagAnswerStatus.INSUFFICIENT_CONTEXT && !citations.isEmpty()) throw new IllegalArgumentException("无法确认结果不能包含引用");
        if (status == RagAnswerStatus.INSUFFICIENT_CONTEXT && !INSUFFICIENT_CONTEXT_MESSAGE.equals(answer)) throw new IllegalArgumentException("无法确认结果必须使用固定文案");
    }

    /** 创建不调用模型或模型无法提供有效依据时的固定拒答。 */
    public static RagAnswer insufficientContext() {
        return new RagAnswer(RagAnswerStatus.INSUFFICIENT_CONTEXT, INSUFFICIENT_CONTEXT_MESSAGE, List.of());
    }
}