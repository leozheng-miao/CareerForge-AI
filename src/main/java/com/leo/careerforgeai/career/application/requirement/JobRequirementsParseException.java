package com.leo.careerforgeai.career.application.requirement;

import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;

/**
 * @program: CareerForge-AI
 * @description: 保存岗位解析失败类型及已经观测到的内部模型Token和耗时。
 * @author: Miao Zheng
 * @date: 2026-08-07 00:40
 **/
public final class JobRequirementsParseException extends ModelException {

    private final ModelUsage modelUsage;
    private final long modelDurationMs;

    /** 创建包含可选模型Token和确定模型耗时的岗位解析异常。 */
    public JobRequirementsParseException(ModelErrorType errorType, String message, Throwable cause,
                                         ModelUsage modelUsage, long modelDurationMs) {
        super(errorType, message, cause);
        if (modelDurationMs < 0) throw new IllegalArgumentException("modelDurationMs 不能小于 0");
        if (modelUsage != null && (modelUsage.inputTokens() < 0
                || modelUsage.outputTokens() < 0
                || modelUsage.totalTokens() < 0)) {
            throw new IllegalArgumentException("modelUsage 不能包含负数");
        }
        this.modelUsage = modelUsage;
        this.modelDurationMs = modelDurationMs;
    }

    /** 返回供应商已经返回的Token，无法观测时返回null。 */
    public ModelUsage getModelUsage() {
        return modelUsage;
    }

    /** 返回岗位解析内部模型调用的已观测耗时。 */
    public long getModelDurationMs() {
        return modelDurationMs;
    }
}