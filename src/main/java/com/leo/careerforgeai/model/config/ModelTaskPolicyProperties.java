package com.leo.careerforgeai.model.config;

import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 统一管理各业务任务的模型请求参数并校验其不超过路由Profile硬上限
 * @author: Miao Zheng
 * @date: 2026-09-04
 * @param tasks 任务类型对应的后端模型请求策略
 */
@ConfigurationProperties(prefix = "careerforge.model-task-policy", ignoreUnknownFields = false)
@Validated
public record ModelTaskPolicyProperties(
        @NotEmpty Map<@NotNull ModelTaskType, @Valid Policy> tasks
) {
    public ModelTaskPolicyProperties {
        if (tasks == null || tasks.isEmpty()) throw new IllegalArgumentException("模型任务策略不能为空");
        EnumMap<ModelTaskType, Policy> copied = new EnumMap<>(ModelTaskType.class);
        tasks.forEach((taskType, policy) -> {
            if (taskType == null || policy == null) throw new IllegalArgumentException("模型任务策略不能包含null");
            copied.put(taskType, policy);
        });
        tasks = Map.copyOf(copied);
    }

    public Policy require(ModelTaskType taskType) {
        Policy policy = tasks.get(Objects.requireNonNull(taskType, "taskType不能为空"));
        if (policy == null) throw new IllegalArgumentException("未配置模型任务策略: " + taskType);
        return policy;
    }

    public ModelRequest apply(ModelTaskType taskType, ModelRequest request) {
        Objects.requireNonNull(request, "request不能为空");
        Policy policy = require(taskType);
        Duration timeout = request.timeout().compareTo(policy.timeout()) < 0
                ? request.timeout() : policy.timeout();
        return new ModelRequest(request.messages(), request.outputFormat(),
                policy.maxOutputTokens(), policy.temperature(), timeout);
    }

    public void validateAgainst(Map<ModelTaskType, List<ModelExecutionProfile>> routes) {
        Objects.requireNonNull(routes, "routes不能为空");
        if (!routes.keySet().equals(tasks.keySet())) {
            throw new IllegalArgumentException("模型任务策略必须与正式路由任务完全一致");
        }
        tasks.forEach((taskType, policy) -> {
            boolean supported = routes.get(taskType).stream()
                    .filter(ModelExecutionProfile::enabled)
                    .anyMatch(profile -> profile.maxOutputTokens() >= policy.maxOutputTokens());
            if (!supported) throw new IllegalArgumentException("任务策略超过所有可用Profile上限: " + taskType);
        });
    }

    /**
     * @param maxOutputTokens 单次调用实际请求的最大输出Token
     * @param temperature 生成温度
     * @param timeout 业务任务允许的单次调用超时
     * @param fallbackAllowed 是否允许Executor切换到后备Profile
     */
    public record Policy(
            int maxOutputTokens,
            double temperature,
            @NotNull Duration timeout,
            boolean fallbackAllowed
    ) {
        public Policy {
            if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens必须大于0");
            if (!Double.isFinite(temperature) || temperature < 0 || temperature > 2) {
                throw new IllegalArgumentException("temperature必须在0到2之间");
            }
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout必须大于0");
            }
        }
    }
}