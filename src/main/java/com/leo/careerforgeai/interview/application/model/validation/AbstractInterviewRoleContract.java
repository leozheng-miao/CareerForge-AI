package com.leo.careerforgeai.interview.application.model.validation;

import com.leo.careerforgeai.interview.application.model.contract.InterviewRoleContract;
import com.leo.careerforgeai.interview.domain.InterviewRole;
import jakarta.validation.Validator;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 统一处理角色类型、Spring AI JSON Schema和Jakarta Bean Validation
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param <I> 角色输入类型
 * @param <O> 角色结构化输出类型
 **/
public abstract class AbstractInterviewRoleContract<I, O> implements InterviewRoleContract<I, O> {

    private final InterviewRole role;
    private final Class<O> outputType;
    private final Validator validator;
    private final String outputJsonSchema;

    protected AbstractInterviewRoleContract(InterviewRole role, Class<O> outputType, Validator validator) {
        this.role = Objects.requireNonNull(role, "role不能为空");
        this.outputType = Objects.requireNonNull(outputType, "outputType不能为空");
        this.validator = Objects.requireNonNull(validator, "validator不能为空");
        this.outputJsonSchema = new BeanOutputConverter<>(outputType).getJsonSchema();
    }

    @Override
    public final InterviewRole role() {
        return role;
    }

    @Override
    public final Class<O> outputType() {
        return outputType;
    }

    @Override
    public final String outputJsonSchema() {
        return outputJsonSchema;
    }

    @Override
    public final void validateInput(I input) {
        validateBean(input, InterviewRoleContractErrorType.INPUT_INVALID, "角色输入结构校验失败");
        validateInputRules(input);
    }

    @Override
    public final O validateOutput(I input, O output) {
        validateInput(input);
        validateBean(output, InterviewRoleContractErrorType.OUTPUT_INVALID, "模型输出结构校验失败");
        validateOutputRules(input, output);
        return output;
    }

    protected abstract void validateInputRules(I input);

    protected abstract void validateOutputRules(I input, O output);

    protected final void requireNoDuplicateInput(Collection<String> values, String fieldName) {
        requireNoDuplicates(values, fieldName, InterviewRoleContractErrorType.INPUT_INVALID);
    }

    protected final void requireNoDuplicateOutput(Collection<String> values, String fieldName) {
        requireNoDuplicates(values, fieldName, InterviewRoleContractErrorType.OUTPUT_INVALID);
    }

    private void requireNoDuplicates(
            Collection<String> values,
            String fieldName,
            InterviewRoleContractErrorType errorType
    ) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (!normalized.add(value.strip())) reject(errorType, fieldName + "包含重复内容");
        }
    }
    protected final void requireAllowedReferences(
            Collection<String> references,
            Collection<String> allowedReferences
    ) {
        if (!Set.copyOf(allowedReferences).containsAll(references)) {
            reject(InterviewRoleContractErrorType.REFERENCE_NOT_ALLOWED, "模型输出包含未授权证据引用");
        }
    }

    protected final void reject(InterviewRoleContractErrorType errorType, String safeMessage) {
        throw new InterviewRoleContractException(role, errorType, safeMessage);
    }

    private <T> void validateBean(T value, InterviewRoleContractErrorType errorType, String safeMessage) {
        if (value == null || !validator.validate(value).isEmpty()) reject(errorType, safeMessage);
    }
}