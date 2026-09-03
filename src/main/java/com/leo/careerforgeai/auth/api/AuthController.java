package com.leo.careerforgeai.auth.api;

import com.leo.careerforgeai.auth.application.AuthApplicationService;
import com.leo.careerforgeai.auth.application.AuthApplicationService.AuthResult;
import com.leo.careerforgeai.auth.application.AuthApplicationService.CurrentUser;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Objects;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import com.leo.careerforgeai.auth.application.AuthException;
import com.leo.careerforgeai.auth.application.port.AuthLoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;

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

    public AuthController(
            AuthApplicationService service,
            AuthLoginRateLimiter loginRateLimiter
    ) {
        this.service = Objects.requireNonNull(service, "service不能为空");
        this.loginRateLimiter = Objects.requireNonNull(loginRateLimiter, "loginRateLimiter不能为空");
    }
    @PostMapping("/auth/register")
    @Operation(summary = "注册账户并签发首组Token")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "注册成功"),
            @ApiResponse(responseCode = "400", description = "邮箱、展示名称或密码不合法"),
            @ApiResponse(responseCode = "409", description = "邮箱已经注册")
    })
    public ResponseEntity<BaseResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = AuthResponse.from(
                service.register(request.email(), request.displayName(), request.password())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ResultUtils.success(response));
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
    public BaseResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        if (!loginRateLimiter.tryAcquire(servletRequest.getRemoteAddr(), request.email())) {
            throw new AuthException(AuthException.Reason.LOGIN_RATE_LIMITED);
        }
        return ResultUtils.success(
                AuthResponse.from(service.login(request.email(), request.password()))
        );
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "轮换Refresh Token并签发新Access Token")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token轮换成功"),
            @ApiResponse(responseCode = "401", description = "Refresh Token无效、过期或发生重放"),
            @ApiResponse(responseCode = "403", description = "账户已禁用"),
            @ApiResponse(responseCode = "409", description = "认证状态发生并发冲突")
    })
    public BaseResponse<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResultUtils.success(
                AuthResponse.from(service.refresh(request.refreshToken()))
        );
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "撤销当前用户指定的Refresh Token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "退出完成，重复调用仍视为成功"),
            @ApiResponse(responseCode = "401", description = "Access Token无效或已过期")
    })
    public BaseResponse<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        service.logout(request.refreshToken());
        return ResultUtils.success(null);
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

    /**
     * @program: CareerForge-AI
     * @description: 注册账户请求
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param email 登录邮箱
     * @param displayName 展示名称
     * @param password 原始密码，仅用于本次请求
     **/
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    /**
     * @program: CareerForge-AI
     * @description: 邮箱密码登录请求
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param email 登录邮箱
     * @param password 原始密码，仅用于本次请求
     **/
    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    /**
     * @program: CareerForge-AI
     * @description: Refresh Token轮换或撤销请求
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param refreshToken 原始Refresh Token，只允许通过JSON请求体提交
     **/
    public record RefreshTokenRequest(
            @NotBlank @Size(max = 512) String refreshToken
    ) {
    }

    /**
     * @program: CareerForge-AI
     * @description: 注册、登录和刷新成功后的前端Token契约
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param userId 当前用户ID
     * @param email 当前用户邮箱
     * @param displayName 当前展示名称
     * @param tokenType Access Token类型
     * @param accessToken Access Token
     * @param accessTokenExpiresAt Access Token过期时间
     * @param refreshToken Refresh Token
     * @param refreshTokenExpiresAt Refresh Token过期时间
     **/
    public record AuthResponse(
            String userId,
            String email,
            String displayName,
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
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
                    result.refreshToken(),
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