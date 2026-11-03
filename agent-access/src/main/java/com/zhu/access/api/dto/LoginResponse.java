package com.zhu.access.api.dto;

/**
 * 登录成功后的会话令牌。后续请求用 Authorization Bearer 带回。
 *
 * @param accessToken 不透明令牌。只存在 Redis，进程不落库
 * @param expiresIn   有效秒数
 * @param tenantId    登录时核对过的租户
 * @param userId      登录时核对过的用户
 * @param displayName 页面上显示的名字。会话键里不放
 */
public record LoginResponse(
        String accessToken,
        long expiresIn,
        String tenantId,
        String userId,
        String displayName) {
}
