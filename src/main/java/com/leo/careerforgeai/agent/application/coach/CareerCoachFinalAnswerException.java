package com.leo.careerforgeai.agent.application.coach;

import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示Career Coach最终回答无法通过Java可信边界校验
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
public final class CareerCoachFinalAnswerException extends RuntimeException {

    private final CareerCoachFinalAnswerErrorType errorType;
    private final AgentRunTrace trace;

    /** 创建尚未关联Agent Trace的最终回答校验异常。 */
    public CareerCoachFinalAnswerException(
            CareerCoachFinalAnswerErrorType errorType,
            String safeMessage
    ) {
        this(errorType, safeMessage, null, null);
    }

    /** 创建保留内部原因但尚未关联Agent Trace的最终回答校验异常。 */
    public CareerCoachFinalAnswerException(
            CareerCoachFinalAnswerErrorType errorType,
            String safeMessage,
            Throwable cause
    ) {
        this(errorType, safeMessage, cause, null);
    }

    private CareerCoachFinalAnswerException(
            CareerCoachFinalAnswerErrorType errorType,
            String safeMessage,
            Throwable cause,
            AgentRunTrace trace
    ) {
        super(safeMessage, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
        this.trace = trace;
    }

    /** 返回一个关联真实Agent Trace的新异常，不修改原异常对象。 */
    public CareerCoachFinalAnswerException withTrace(AgentRunTrace trace) {
        Objects.requireNonNull(trace, "trace不能为空");
        return new CareerCoachFinalAnswerException(errorType, getMessage(), this, trace);
    }

    /** 返回稳定的最终回答错误分类。 */
    public CareerCoachFinalAnswerErrorType getErrorType() {
        return errorType;
    }

    /** 返回已完成模型调用的脱敏Trace；Validator单独使用时可能为空。 */
    public AgentRunTrace getTrace() {
        return trace;
    }
}