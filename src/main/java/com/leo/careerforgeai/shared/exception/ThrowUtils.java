package com.leo.careerforgeai.shared.exception;

/**
 * @program: yu-picture
 * @description: 异常处理工具类
 * @author: Miao Zheng
 * @date: 2025-10-20 16:30
 **/
public class ThrowUtils {

    public static void throwIf(boolean condition, RuntimeException e) {
        if (condition) {
            throw e;
        }
    }

    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * @param condition
     * @param errorCode
     * @param message
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }


}