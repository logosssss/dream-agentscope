package com.zhu.access.api.controller.auth;

import com.zhu.access.api.dto.auth.LoginRequest;
import com.zhu.access.api.dto.auth.LoginResponse;
import com.zhu.access.application.auth.session.ClientAccountService;
import com.zhu.access.application.auth.session.UserLoginService;
import com.zhu.access.infrastructure.security.AccessIdentityFilter;
import com.zhu.common.dto.ApiResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端密码注册、登录和退出。仍要先过调用方令牌。
 * 不接受租户和用户标识。
 */
@RestController
public class LoginController {

    private final ClientAccountService accounts;

    private final UserLoginService logins;

    /** 注册和登录走账号服务。退出只删会话。 */
    public LoginController(ClientAccountService accounts, UserLoginService logins) {
        this.accounts = accounts;
        this.logins = logins;
    }

    /**
     * 建号并签发会话。账号已存在时不发令牌。
     * 请求体为空时按空字段处理，由服务拒绝。
     */
    @PostMapping("/api/register")
    public ApiResult<LoginResponse> register(@RequestBody(required = false) LoginRequest request) {
        LoginRequest body = request == null ? new LoginRequest(null, null) : request;
        return ApiResult.ok(accounts.register(body.accountName(), body.password()));
    }

    /**
     * 核对密码后签发会话。失败不说明是账号错了还是密码错了。
     */
    @PostMapping("/api/login")
    public ApiResult<LoginResponse> login(@RequestBody(required = false) LoginRequest request) {
        LoginRequest body = request == null ? new LoginRequest(null, null) : request;
        return ApiResult.ok(accounts.login(body.accountName(), body.password()));
    }

    /**
     * 优先用请求体里的令牌；没有则读 Authorization Bearer。
     * 退出不要求已经登录，避免会话过期后无法清理。
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
