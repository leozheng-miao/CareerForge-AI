package com.leo.careerforgeai.agent.domain.tool.career.search;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 保存职业材料搜索的有界证据、候选统计和安全业务状态。
 * @author: Miao Zheng
 * @date: 2026-08-06 21:00
 **/
public record SearchCareerMaterialsOutput(
        SearchCareerMaterialsStatus status,
        String requestId,
        List<CareerMaterialEvidence> evidence,
        int usedContentChars,
        int candidateCount,
        SearchCareerMaterialsErrorType errorType
) {

    private static final int MAX_REQUEST_ID_CHARS = 128;
    private static final int MAX_EVIDENCE_ITEMS = 5;
    private static final int MAX_CANDIDATE_COUNT = 100;

    public SearchCareerMaterialsOutput {
        if (status == null) throw new IllegalArgumentException("status 不能为空");
        validateRequestId(requestId);
        if (evidence == null || evidence.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("evidence 不能为空且不能包含 null");
        }
        evidence = List.copyOf(evidence);

        if (evidence.size() > MAX_EVIDENCE_ITEMS) {
            throw new IllegalArgumentException("evidence 数量超过限制");
        }
        if (usedContentChars < 0) throw new IllegalArgumentException("usedContentChars 不能小于 0");
        if (candidateCount < 0 || candidateCount > MAX_CANDIDATE_COUNT) {
            throw new IllegalArgumentException("candidateCount 超出限制");
        }
        if (candidateCount < evidence.size()) {
            throw new IllegalArgumentException("candidateCount 不能小于 evidence 数量");
        }

        int actualContentChars = evidence.stream()
                .mapToInt(item -> item.content().length())
                .sum();
        if (usedContentChars != actualContentChars) {
            throw new IllegalArgumentException("usedContentChars 与 evidence 内容长度不一致");
        }

        validateStatus(status, evidence, usedContentChars, candidateCount, errorType);
    }

    /** 根据证据集合自动创建SUCCESS或NO_EVIDENCE结果。 */
    public static SearchCareerMaterialsOutput fromEvidence(
            String requestId,
            List<CareerMaterialEvidence> evidence,
            int candidateCount
    ) {
        SearchCareerMaterialsStatus status = evidence.isEmpty()
                ? SearchCareerMaterialsStatus.NO_EVIDENCE
                : SearchCareerMaterialsStatus.SUCCESS;
        int usedContentChars = evidence.stream()
                .mapToInt(item -> item.content().length())
                .sum();

        return new SearchCareerMaterialsOutput(
                status,
                requestId,
                evidence,
                usedContentChars,
                candidateCount,
                null
        );
    }

    /** 创建不包含内部异常详情的系统错误结果。 */
    public static SearchCareerMaterialsOutput systemError(
            String requestId,
            SearchCareerMaterialsErrorType errorType
    ) {
        return new SearchCareerMaterialsOutput(
                SearchCareerMaterialsStatus.SYSTEM_ERROR,
                requestId,
                List.of(),
                0,
                0,
                errorType
        );
    }

    /** 创建不包含部分证据的超时结果。 */
    public static SearchCareerMaterialsOutput timeout(
            String requestId,
            SearchCareerMaterialsErrorType errorType
    ) {
        return new SearchCareerMaterialsOutput(
                SearchCareerMaterialsStatus.TIMEOUT,
                requestId,
                List.of(),
                0,
                0,
                errorType
        );
    }

    /** 校验业务状态、证据和错误类型组合合法。 */
    private static void validateStatus(
            SearchCareerMaterialsStatus status,
            List<CareerMaterialEvidence> evidence,
            int usedContentChars,
            int candidateCount,
            SearchCareerMaterialsErrorType errorType
    ) {
        switch (status) {
            case SUCCESS -> {
                if (evidence.isEmpty()) throw new IllegalArgumentException("SUCCESS 必须包含 evidence");
                if (errorType != null) throw new IllegalArgumentException("SUCCESS 不能包含 errorType");
            }
            case NO_EVIDENCE -> {
                if (!evidence.isEmpty() || usedContentChars != 0 || errorType != null) {
                    throw new IllegalArgumentException("NO_EVIDENCE 不能包含证据或错误");
                }
            }
            case SYSTEM_ERROR -> {
                if (!evidence.isEmpty() || usedContentChars != 0 || candidateCount != 0) {
                    throw new IllegalArgumentException("SYSTEM_ERROR 不能包含部分证据");
                }
                if (errorType != SearchCareerMaterialsErrorType.RETRIEVAL_FAILED
                        && errorType != SearchCareerMaterialsErrorType.INTERNAL_ERROR) {
                    throw new IllegalArgumentException("SYSTEM_ERROR 的 errorType 非法");
                }
            }
            case TIMEOUT -> {
                if (!evidence.isEmpty() || usedContentChars != 0 || candidateCount != 0) {
                    throw new IllegalArgumentException("TIMEOUT 不能包含部分证据");
                }
                if (errorType != SearchCareerMaterialsErrorType.UPSTREAM_TIMEOUT
                        && errorType != SearchCareerMaterialsErrorType.AGENT_DEADLINE_EXCEEDED) {
                    throw new IllegalArgumentException("TIMEOUT 的 errorType 非法");
                }
            }
        }
    }

    /** 校验关联ID长度和日志安全字符。 */
    private static void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId 不能为空");
        if (requestId.length() > MAX_REQUEST_ID_CHARS) throw new IllegalArgumentException("requestId 超过长度限制");
        if (!requestId.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("requestId 包含非法字符");
        }
    }
}