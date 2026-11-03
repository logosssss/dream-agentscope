package com.zhu.audit.api;

/**
 * 一次客户端登记或核对的结果。不带密码和 openid。
 *
 * @param tenantId    凭证所在租户
 * @param userId      用户业务标识
 * @param displayName 显示名。可以空
 * @param active      tenant_user 仍是 ACTIVE
 */
public record RegisteredClient(String tenantId, String userId, String displayName, boolean active) {
}
