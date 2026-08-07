package com.leo.careerforgeai.agent.domain.tool.career;

import com.leo.careerforgeai.career.domain.JobRequirements;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存岗位解析工具的结构化要求及安全业务状态。
 * @author: Miao Zheng
 * @date: 2026-08-07 01:10
 **/
public record ParseJobRequirementsOutput(
        ParseJobRequirementsStatus status,
        JobRequirements requirements,
        ParseJobRequirementsErrorType errorType
) {

    private static final int MAX_JOB_TITLE_CHARS = 200;
    private static final int MAX_ITEMS_PER_CATEGORY = 30;
    private static final int MAX_ITEM_CHARS = 500;
    private static final int MAX_TOTAL_ITEMS = 120;
    private static final int MAX_TOTAL_CHARS = 12_000;

    public ParseJobRequirementsOutput {
        Objects.requireNonNull(status, "status 不能为空");

        switch (status) {
            case SUCCESS -> {
                Objects.requireNonNull(requirements, "SUCCESS 必须包含 requirements");
                if (errorType != null) throw new IllegalArgumentException("SUCCESS 不能包含 errorType");
                validateRequirements(requirements);
            }
            case SYSTEM_ERROR -> {
                if (requirements != null) throw new IllegalArgumentException("SYSTEM_ERROR 不能包含 requirements");
                if (errorType != ParseJobRequirementsErrorType.MODEL_CALL_FAILED
                        && errorType != ParseJobRequirementsErrorType.MODEL_OUTPUT_INVALID
                        && errorType != ParseJobRequirementsErrorType.INTERNAL_ERROR) {
                    throw new IllegalArgumentException("SYSTEM_ERROR 的 errorType 非法");
                }
            }
            case TIMEOUT -> {
                if (requirements != null) throw new IllegalArgumentException("TIMEOUT 不能包含 requirements");
                if (errorType != ParseJobRequirementsErrorType.UPSTREAM_TIMEOUT) {
                    throw new IllegalArgumentException("TIMEOUT 的 errorType 非法");
                }
            }
        }
    }

    /** 创建包含结构化岗位要求的成功结果。 */
    public static ParseJobRequirementsOutput success(JobRequirements requirements) {
        return new ParseJobRequirementsOutput(
                ParseJobRequirementsStatus.SUCCESS,
                requirements,
                null
        );
    }

    /** 创建不包含内部异常内容的系统失败结果。 */
    public static ParseJobRequirementsOutput systemError(ParseJobRequirementsErrorType errorType) {
        return new ParseJobRequirementsOutput(
                ParseJobRequirementsStatus.SYSTEM_ERROR,
                null,
                errorType
        );
    }

    /** 创建不包含部分解析结果的上游超时结果。 */
    public static ParseJobRequirementsOutput timeout() {
        return new ParseJobRequirementsOutput(
                ParseJobRequirementsStatus.TIMEOUT,
                null,
                ParseJobRequirementsErrorType.UPSTREAM_TIMEOUT
        );
    }

    /** 验证岗位标题和所有分类集合都符合Tool Result预算。 */
    private static void validateRequirements(JobRequirements requirements) {
        if (requirements.jobTitle() == null || requirements.jobTitle().isBlank()) {
            throw new IllegalArgumentException("jobTitle 不能为空");
        }
        if (requirements.jobTitle().length() > MAX_JOB_TITLE_CHARS) {
            throw new IllegalArgumentException("jobTitle 超过长度限制");
        }

        validateItems("programmingLanguages", requirements.programmingLanguages());
        validateItems("backendAndInfrastructureRequirements", requirements.backendAndInfrastructureRequirements());
        validateItems("agentRequirements", requirements.agentRequirements());
        validateItems("ragRequirements", requirements.ragRequirements());
        validateItems("engineeringRequirements", requirements.engineeringRequirements());
        validateItems("bonusQualifications", requirements.bonusQualifications());
        validateItems("responsibilities", requirements.responsibilities());
        validateItems("interviewTopics", requirements.interviewTopics());
        List<List<String>> categories = List.of(
                requirements.programmingLanguages(),
                requirements.backendAndInfrastructureRequirements(),
                requirements.agentRequirements(),
                requirements.ragRequirements(),
                requirements.engineeringRequirements(),
                requirements.bonusQualifications(),
                requirements.responsibilities(),
                requirements.interviewTopics()
        );

        int totalItems = categories.stream().mapToInt(List::size).sum();
        if (totalItems > MAX_TOTAL_ITEMS) {
            throw new IllegalArgumentException("岗位要求总数量超过限制");
        }

        int totalChars = requirements.jobTitle().length()
                + categories.stream().flatMap(List::stream).mapToInt(String::length).sum();
        if (totalChars > MAX_TOTAL_CHARS) {
            throw new IllegalArgumentException("岗位要求总字符数超过限制");
        }
    }

    /** 验证单个岗位要求分类的数量和文本长度。 */
    private static void validateItems(String fieldName, List<String> items) {
        if (items == null) throw new IllegalArgumentException(fieldName + " 不能为空");
        if (items.size() > MAX_ITEMS_PER_CATEGORY) {
            throw new IllegalArgumentException(fieldName + " 数量超过限制");
        }
        if (items.stream().anyMatch(item -> item == null || item.isBlank() || item.length() > MAX_ITEM_CHARS)) {
            throw new IllegalArgumentException(fieldName + " 包含非法内容");
        }
    }
}