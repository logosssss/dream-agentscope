package com.zhu.access.api.dto.auth;

/**
 * 当前请求身份。显示名来自 tenant_user，MySQL 关闭时为空。
 *
 * @param tenantId    请求头或登录会话里的租户
 * @param userId      请求头或登录会话里的用户
 * @param displayName 登记显示名，没有或未开 MySQL 时为空
 * @param status      登记状态，未开 MySQL 时为空
 */
public record CurrentUser(String tenantId, String userId, String displayName, String status) {
}
