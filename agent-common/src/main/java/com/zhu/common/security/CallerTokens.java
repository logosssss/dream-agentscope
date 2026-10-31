package com.zhu.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 调用方令牌的比较。这不是用户登录，不保存用户表。
 */
public final class CallerTokens {

    /** 两个进程共用的请求头。期望值各自从环境变量来。 */
    public static final String HEADER = "X-Caller-Token";

    private CallerTokens() {
    }

    /**
     * 存活和就绪不校验令牌。路径已经去掉 context path。
     */
    public static boolean isHealth(String path) {
        return "/health/live".equals(path) || "/health/ready".equals(path);
    }

    /**
     * 配置为空、请求头为空，或两边字节不一致时都不通过。
     * 用定长比较，避免提前返回把令牌内容露出去。长度不同仍会马上失败，这是 JDK 的行为。
     */
    public static boolean matches(String expected, String presented) {
        if (expected == null || expected.isBlank() || presented == null || presented.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
