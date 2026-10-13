package com.leo.careerforgeai.auth.api;

import com.leo.careerforgeai.auth.application.AuthApplicationService;
import com.leo.careerforgeai.auth.application.AuthApplicationService.AuthResult;
import com.leo.careerforgeai.auth.application.AuthApplicationService.CurrentUser;
import com.leo.careerforgeai.auth.application.AuthException;
import com.leo.careerforgeai.auth.application.port.AuthLoginRateLimiter;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 提供注册、登录、刷新、退出和当前账户查询API
 * @author: Miao Zheng
 * @date: 2026-09-03
 **/
@RestController
@RequestMapping("/api")
@Tag(name = "Authentication")
@ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
public class AuthController {
    private final AuthApplicationService service;
    private final AuthLoginRateLimiter loginRateLimiter;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    public AuthController(AuthApplicationService service,
                          AuthLoginRateLimiter loginRateLimiter,
                          RefreshTokenCookieManager refreshTokenCookieManager) {
        this.service = Objects.requireNonNull(service, "service不能为空");
        this.loginRateLimiter = Objects.requireNonNull(loginRateLimiter, "loginRateLimiter不能为空");
        this.refreshTokenCookieManager = Objects.requireNonNull(
                refreshTokenCookieManager, "refreshTokenCookieManager不能为空");
    }

    @PostMapping("/auth/register")
    @Operation(summary = "注册账户并建立登录会话")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "注册成功"),
            @ApiResponse(responseCode = "400", description = "邮箱、展示名称或密码不合法"),
            @ApiResponse(responseCode = "409", description = "邮箱已经注册")
    })
    public ResponseEntity<BaseResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse servletResponse) {
        return sessionResponse(service.register(
                request.email(), request.displayName(), request.password()),
                HttpStatus.CREATED, servletResponse);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "使用邮箱和密码登录")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登录成功"),
            @ApiResponse(responseCode = "401", description = "邮箱或密码错误"),
            @ApiResponse(responseCode = "403", description = "账户已禁用"),
            @ApiResponse(responseCode = "429", description = "登录尝试过于频繁"),
            @ApiResponse(responseCode = "503", description = "登录安全基础设施暂时不可用")
    })
    public ResponseEntity<BaseResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        if (!loginRateLimiter.tryAcquire(servletRequest.getRemoteAddr(), request.email())) {
            throw new AuthException(AuthException.Reason.LOGIN_RATE_LIMITED);
        }
        return sessionResponse(service.login(request.email(), request.password()),
                HttpStatus.OK, servletResponse);
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "通过HttpOnly Cookie轮换Refresh Token并签发新Access Token")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token轮换成功"),
            @ApiResponse(responseCode = "401", description = "Refresh Token无效、过期或发生重放"),
            @ApiResponse(responseCode = "403", description = "账户已禁用"),
            @ApiResponse(responseCode = "409", description = "认证状态发生并发冲突")
    })
    public ResponseEntity<BaseResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = refreshTokenCookieManager.read(request)
                .orElseThrow(() -> new AuthException(AuthException.Reason.REFRESH_TOKEN_INVALID));
        try {
            return sessionResponse(service.refresh(refreshToken), HttpStatus.OK, response);
        } catch (AuthException exception) {
            if (shouldClearRefreshCookie(exception.reason())) {
                refreshTokenCookieManager.clear(response);
            }
            throw exception;
        }
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "暂停进行中面试、撤销Refresh Token并清除登录Cookie")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "退出完成，重复调用仍视为成功"),
            @ApiResponse(responseCode = "503", description = "认证持久化暂时不可用")
    })
    public ResponseEntity<BaseResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            refreshTokenCookieManager.read(request).ifPresent(service::logout);
        } finally {
            refreshTokenCookieManager.clear(response);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ResultUtils.success(null));
    }

    @GetMapping("/me")
    @Operation(summary = "查询当前认证账户")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "返回当前账户"),
            @ApiResponse(responseCode = "401", description = "Access Token无效或已过期"),
            @ApiResponse(responseCode = "403", description = "账户已禁用")
    })
    public BaseResponse<CurrentUserResponse> me() {
        return ResultUtils.success(CurrentUserResponse.from(service.me()));
    }

    private ResponseEntity<BaseResponse<AuthResponse>> sessionResponse(
            AuthResult result,
            HttpStatus status,
            HttpServletResponse servletResponse) {
        refreshTokenCookieManager.issue(servletResponse, result.refreshToken());
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(ResultUtils.success(AuthResponse.from(result)));
    }

    private static boolean shouldClearRefreshCookie(AuthException.Reason reason) {
        return reason == AuthException.Reason.REFRESH_TOKEN_INVALID
                || reason == AuthException.Reason.REFRESH_TOKEN_REPLAYED
                || reason == AuthException.Reason.ACCOUNT_DISABLED;
    }

    /**
     * @program: CareerForge-AI
     * @description: 注册账户请求
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param email 登录邮箱
     * @param displayName 展示名称
     * @param password 原始密码
     **/
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {}

    /**
     * @program: CareerForge-AI
     * @description: 邮箱密码登录请求
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param email 登录邮箱
     * @param password 原始密码
     **/
    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password
    ) {}

    /**
     * @program: CareerForge-AI
     * @description: 注册、登录和刷新成功后的前端会话契约
     * @author: Miao Zheng
     * @date: 2026-09-08
     * @param userId 当前用户ID
     * @param email 当前用户邮箱
     * @param displayName 当前展示名称
     * @param tokenType Access Token类型
     * @param accessToken Access Token
     * @param accessTokenExpiresAt Access Token过期时间
     * @param refreshTokenExpiresAt Refresh Token过期时间
     **/
    public record AuthResponse(
            String userId,
            String email,
            String displayName,
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt
    ) {
        static AuthResponse from(AuthResult result) {
            return new AuthResponse(
                    result.userId().value(),
                    result.email(),
                    result.displayName(),
                    result.tokenType(),
                    result.accessToken(),
                    result.accessTokenExpiresAt(),
                    result.refreshTokenExpiresAt()
            );
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 当前认证账户的非敏感信息
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param userId 当前用户ID
     * @param email 当前用户邮箱
     * @param displayName 当前展示名称
     * @param createdAt 注册时间
     * @param lastLoginAt 最近登录时间
     **/
    public record CurrentUserResponse(
            String userId,
            String email,
            String displayName,
            Instant createdAt,
            Instant lastLoginAt
    ) {
        static CurrentUserResponse from(CurrentUser user) {
            return new CurrentUserResponse(
                    user.userId().value(),
                    user.email(),
                    user.displayName(),
                    user.createdAt(),
                    user.lastLoginAt()
            );
        }
    }
}
