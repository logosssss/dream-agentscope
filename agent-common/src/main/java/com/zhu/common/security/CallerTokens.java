package com.zhu.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/**
 * 调用方令牌的比较。这不是用户登录，不保存用户表。
 */
public final class CallerTokens {

    /** 两个进程共用的请求头。期望值各自从环境变量来。 */
    public static final String HEADER = "X-Caller-Token";

    /**
     * 文档与部署样例里出现过的占位串。配成正式令牌等于裸奔，一律拒收。
     */
    private static final Set<String> INSECURE_PLACEHOLDERS = Set.of(
            "change-me",
            "change-me-caller",
            "change-me-admin",
            "replace_with_agentscope_caller_token",
            "replace_with_agentscope_admin_token");

    private CallerTokens() {
    }

    /**
     * 存活和就绪不校验令牌。路径已经去掉 context path。
     */
    public static boolean isHealth(String path) {
        return "/health/live".equals(path) || "/health/ready".equals(path);
    }

    /**
     * 小程序确认登录不带调用方令牌。票是网站先申请的。
     */
    public static boolean isWxConfirm(String path) {
        return "/api/public/auth/wx/confirm".equals(path);
    }

    /**
     * 是否为文档占位串。大小写不敏感；空白不算占位，交给空白分支处理。
     */
    public static boolean isInsecurePlaceholder(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return INSECURE_PLACEHOLDERS.contains(token.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 启动时校验令牌已配置且不是占位串。名字只用于异常文案。
     */
    public static void requireSecure(String name, String token) {
        String label = name == null || name.isBlank() ? "caller token" : name.trim();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(label + " is required");
        }
        if (isInsecurePlaceholder(token)) {
            throw new IllegalStateException(label + " must not use a documented placeholder");
        }
    }

    /**
     * 配置为空、占位串、请求头为空，或两边字节不一致时都不通过。
     * 用定长比较，避免提前返回把令牌内容露出去。长度不同仍会马上失败，这是 JDK 的行为。
     */
    public static boolean matches(String expected, String presented) {
        if (expected == null || expected.isBlank() || isInsecurePlaceholder(expected)
                || presented == null || presented.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
