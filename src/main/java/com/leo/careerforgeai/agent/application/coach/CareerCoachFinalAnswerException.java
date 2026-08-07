package com.leo.careerforgeai.agent.application.coach;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示Career Coach最终回答无法通过Java可信边界校验。
 * @author: Miao Zheng
 * @date: 2026-08-07 03:30
 **/
public final class CareerCoachFinalAnswerException extends RuntimeException {

    private final CareerCoachFinalAnswerErrorType errorType;

    /** 创建不携带底层异常的最终回答校验异常。 */
    public CareerCoachFinalAnswerException(CareerCoachFinalAnswerErrorType errorType, String safeMessage) {
        this(errorType, safeMessage, null);
    }

    /** 创建携带内部原因但只暴露安全消息的最终回答校验异常。 */
    public CareerCoachFinalAnswerException(CareerCoachFinalAnswerErrorType errorType, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
    }

    /** 返回稳定的最终回答错误分类。 */
    public CareerCoachFinalAnswerErrorType getErrorType() {
        return errorType;
    }
}