package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.application.model.contract.InterviewRoleContract;
import com.leo.careerforgeai.model.domain.ModelUsage;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 定义四个面试角色共用的供应商无关结构化模型调用端口
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public interface InterviewRoleModelGateway {

    <I, O> Result<O> generate(InterviewRoleContract<I, O> contract, I input, Duration timeout);

    /**
     * @program: CareerForge-AI
     * @description: 返回已经过角色契约校验的模型输出及安全调用元数据
     * @author: Miao Zheng
     * @date: 2026-08-27
     * @param output 已通过角色契约校验的结构化输出
     * @param requestId 最后一次模型请求ID
     * @param model 实际模型名称
     * @param promptVersion 角色Prompt版本
     * @param usage 本次逻辑调用累计Token用量
     * @param durationMs 本次逻辑调用累计耗时
     * @param modelCallCount 角色生成次数，只统计初次生成和结构修复，不统计同次生成内部的网络重试
     * @param repaired 是否通过一次结构修复获得有效输出
     * @param responseHash 最终原始响应的小写SHA-256
     * @param <O> 角色结构化输出类型
     **/
    record Result<O>(
            O output,
            String requestId,
            String model,
            String promptVersion,
            ModelUsage usage,
            long durationMs,
            int modelCallCount,
            boolean repaired,
            String responseHash
    ) {
        private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

        public Result {
            Objects.requireNonNull(output, "output不能为空");
            requestId = requireText(requestId, "requestId", 128);
            model = requireText(model, "model", 128);
            promptVersion = requireText(promptVersion, "promptVersion", 64);
            Objects.requireNonNull(usage, "usage不能为空");
            if (usage.inputTokens() < 0 || usage.outputTokens() < 0
                    || usage.totalTokens() != usage.inputTokens() + usage.outputTokens()) {
                throw new IllegalArgumentException("模型Token用量不合法");
            }
            if (durationMs < 0) throw new IllegalArgumentException("durationMs不能小于0");
            if (modelCallCount < 1 || modelCallCount > 2) {
                throw new IllegalArgumentException("modelCallCount必须在1到2之间");
            }
            if (repaired != (modelCallCount == 2)) {
                throw new IllegalArgumentException("repaired必须与modelCallCount一致");
            }
            if (responseHash == null || !SHA256_PATTERN.matcher(responseHash).matches()) {
                throw new IllegalArgumentException("responseHash必须是64位小写SHA-256");
            }
        }

        private static String requireText(String value, String fieldName, int maxLength) {
            if (value == null || value.isBlank() || value.length() > maxLength) {
                throw new IllegalArgumentException(fieldName + "不能为空且长度不能超过" + maxLength);
            }
            return value;
        }
    }
}