package com.leo.careerforgeai.auth.application;

/**
 * @program: CareerForge-AI
 * @description: 表示认证用例可安全返回给API层的稳定失败原因
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
public final class AuthException extends RuntimeException {

    private final Reason reason;

    public AuthException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义注册、登录和Token生命周期的稳定失败语义
     * @author: Miao Zheng
     * @date: 2026-09-02
     **/
    public enum Reason {
        EMAIL_ALREADY_EXISTS("该邮箱已经注册"),
        INVALID_CREDENTIALS("邮箱或密码错误"),
        ACCOUNT_DISABLED("账户已被禁用"),
        REFRESH_TOKEN_INVALID("Refresh Token无效或已过期"),
        LOGIN_RATE_LIMITED("登录尝试过于频繁，请稍后重试"),
        REFRESH_TOKEN_REPLAYED("检测到Refresh Token重复使用，请重新登录"),
        AUTH_STATE_CONFLICT("认证状态已经变化，请重新登录"),
        ACCOUNT_NOT_FOUND("账户不存在");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}