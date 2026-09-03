package com.leo.careerforgeai.shared.exception;

import lombok.Getter;

/**
 * @program:
 * @description:
 * @author: Miao Zheng
 * @date: 2025-10-20 15:38
 **/
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    CONFLICT_ERROR(40900, "请求冲突"),
    TOO_MANY_REQUEST(42900, "请求过于频繁"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    USER_EXIST(40010, "用户名已存在"),
    USER_NOT_EXIST(40011, "用户不存在"),
    PASSWORD_ERROR(40012, "用户名或密码错误"),
    JWT_INVALID(40110, "JWT 无效或已过期"),
    REFRESH_TOKEN_INVALID(40111, "Refresh Token无效或已过期"),
    REFRESH_TOKEN_REPLAYED(40112, "检测到Refresh Token重复使用，请重新登录"),
    ACCOUNT_DISABLED(40310, "账户已被禁用"),
    AUTH_STATE_CONFLICT(40910, "认证状态已经变化，请重新登录"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),
    SERVICE_UNAVAILABLE_ERROR(50300, "服务暂时不可用");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}