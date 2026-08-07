package com.leo.careerforgeai.agent.domain.coach;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存经过Java校验的Career Coach最终回答和合法Chunk引用。
 * @author: Miao Zheng
 * @date: 2026-08-07 02:40
 **/
public record CareerCoachAnswer(
        CareerCoachAnswerStatus status,
        String answer,
        List<String> citedChunkIds
) {

    private static final int MAX_ANSWER_CHARS = 8_000;
    private static final int MAX_CITATIONS = 10;
    private static final Pattern CHUNK_ID_PATTERN =
            Pattern.compile("[0-9a-f]{64}");

    public CareerCoachAnswer {
        Objects.requireNonNull(status, "status 不能为空");
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer 不能为空");
        }
        if (answer.length() > MAX_ANSWER_CHARS) {
            throw new IllegalArgumentException("answer 超过长度限制");
        }
        if (citedChunkIds == null) {
            throw new IllegalArgumentException("citedChunkIds 不能为空");
        }

        if (citedChunkIds.size() > MAX_CITATIONS) {
            throw new IllegalArgumentException("citedChunkIds 数量超过限制");
        }
        if (citedChunkIds.stream().anyMatch(chunkId ->
                chunkId == null || !CHUNK_ID_PATTERN.matcher(chunkId).matches())) {
            throw new IllegalArgumentException("citedChunkIds 包含非法Chunk ID");
        }
        if (new HashSet<>(citedChunkIds).size() != citedChunkIds.size()) {
            throw new IllegalArgumentException("citedChunkIds 不能重复");
        }
        if (status != CareerCoachAnswerStatus.ANSWERED
                && !citedChunkIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "证据不足或拒答状态不能包含引用");
        }

        citedChunkIds = List.copyOf(citedChunkIds);

    }
}