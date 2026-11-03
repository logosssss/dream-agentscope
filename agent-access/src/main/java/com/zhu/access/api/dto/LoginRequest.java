package com.zhu.access.api.dto;

/**
 * 客户端密码登录和注册的入参。不接收租户和用户标识。
 *
 * @param accountName 登录名
 * @param password    密码。登录失败时不说明是哪一项错了
 */
public record LoginRequest(String accountName, String password) {
}
