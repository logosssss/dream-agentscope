package com.zhu.access.api.controller;

import com.zhu.access.api.dto.LoginRequest;
import com.zhu.access.api.dto.LoginResponse;
import com.zhu.access.application.UserLoginService;
import com.zhu.access.infrastructure.security.AccessIdentityFilter;
import com.zhu.common.dto.ApiResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户登录和退出。仍要先过调用方令牌，这不是替代 X-Caller-Token。
 */
@RestController
public class LoginController {

    private final UserLoginService logins;

    /** 登录只依赖会话服务。 */
    public LoginController(UserLoginService logins) {
        this.logins = logins;
    }

    /**
     * 核对租户用户后签发会话令牌。没有密码。
     * 请求体为空时按空字段处理，由服务拒绝。
     */
    @PostMapping("/api/login")
    public ApiResult<LoginResponse> login(@RequestBody(required = false) LoginRequest request) {
        LoginRequest body = request == null ? new LoginRequest(null, null) : request;
        return ApiResult.ok(logins.login(body.tenantId(), body.userId()));
    }

    /**
     * 优先用请求体里的令牌；没有则读 Authorization Bearer。
     * 退出不要求租户用户门，避免会话过期后无法清理。
     */
    @PostMapping("/api/logout")
    public ApiResult<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            @RequestHeader(value = AccessIdentityFilter.AUTHORIZATION_HEADER, required = false) String authorization) {
        String token = request == null ? null : request.accessToken();
        if (token == null || token.isBlank()) {
            token = AccessIdentityFilter.bearerToken(authorization);
        }
        logins.logout(token);
        return ApiResult.ok(null);
    }

    /**
     * 退出时可带令牌。空则尝试请求头。
     *
     * @param accessToken 登录时拿到的令牌
     */
    public record LogoutRequest(String accessToken) {
    }
}
