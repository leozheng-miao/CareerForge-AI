package com.leo.careerforgeai.auth.api;

import com.leo.careerforgeai.auth.application.AuthException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @program: CareerForge-AI
 * @description: 将认证失败映射为稳定HTTP状态和脱敏业务错误
 * @author: Miao Zheng
 * @date: 2026-09-03
 **/
@Slf4j
@RestControllerAdvice(assignableTypes = AuthController.class)
@ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
public class AuthApiExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<BaseResponse<?>> handleAuth(AuthException exception) {
        log.warn("认证请求失败，reason={}", exception.reason());
        return switch (exception.reason()) {
            case EMAIL_ALREADY_EXISTS ->
                    error(HttpStatus.CONFLICT, ErrorCode.USER_EXIST, exception.getMessage());
            case INVALID_CREDENTIALS ->
                    error(HttpStatus.UNAUTHORIZED, ErrorCode.PASSWORD_ERROR, exception.getMessage());
            case ACCOUNT_DISABLED ->
                    error(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_DISABLED, exception.getMessage());
            case LOGIN_RATE_LIMITED ->
                    error(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUEST, exception.getMessage());
            case REFRESH_TOKEN_INVALID ->
                    error(HttpStatus.UNAUTHORIZED, ErrorCode.REFRESH_TOKEN_INVALID, exception.getMessage());
            case REFRESH_TOKEN_REPLAYED ->
                    error(HttpStatus.UNAUTHORIZED, ErrorCode.REFRESH_TOKEN_REPLAYED, exception.getMessage());
            case AUTH_STATE_CONFLICT ->
                    error(HttpStatus.CONFLICT, ErrorCode.AUTH_STATE_CONFLICT, exception.getMessage());
            case ACCOUNT_NOT_FOUND ->
                    error(HttpStatus.UNAUTHORIZED, ErrorCode.NOT_LOGIN_ERROR, "认证账户不存在");
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数不合法");
        return error(HttpStatus.BAD_REQUEST, ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<?>> handleUnreadableJson() {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.PARAMS_ERROR,
                "请求JSON格式或字段不合法"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<?>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.PARAMS_ERROR,
                exception.getMessage()
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<BaseResponse<?>> handleDataAccess(
            DataAccessException exception
    ) {
        log.error("认证持久化失败，errorType={}", exception.getClass().getSimpleName());
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SERVICE_UNAVAILABLE_ERROR,
                "认证服务暂时不可用，请稍后重试"
        );
    }

    private ResponseEntity<BaseResponse<?>> error(
            HttpStatus status,
            ErrorCode errorCode,
            String message
    ) {
        return ResponseEntity.status(status)
                .body(ResultUtils.error(errorCode, message));
    }
}