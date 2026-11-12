package com.zhu.audit.api.tenant;

/**
 * 客户端登录凭证。密码账号和微信账号分开存。
 * MySQL 关闭时没有实现，调用方按不可用处理。
 */
public interface ClientLoginStore {

    /**
     * 写入密码账号和对应的租户用户。登录名重复时拒绝，不签发会话。
     * 租户不存在或不是 ACTIVE 时按不可用处理。
     */
    RegisteredClient registerPassword(
            String tenantId,
            String loginName,
            String salt,
            String passwordHash,
            String userId,
            String displayName);

    /**
     * 登录名和密码都对、且用户仍启用时返回该用户。
     * 找不到、密码不对或已停用都返回 null，避免告诉调用方是哪一种。
     * 租户不可用时抛不可用，而不是伪装成密码错误。
     */
    RegisteredClient matchPassword(String tenantId, String loginName, String password);

    /**
     * 同一个 openid 返回原来的用户。没有则按传入的标识新建。
     * 已停用的用户 active 为 false，不另建一行。
     */
    RegisteredClient wechat(String tenantId, String openId, String userId, String displayName);
}
