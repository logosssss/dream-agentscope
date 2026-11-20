package com.zhu.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/**
 * 公共安全：用户进程内部唤醒令牌比较。
 * 与 Caller / Admin 令牌分离；值只来自环境变量，不进 Nacos 明文。
 * 见 docs/wakeup-落地.md。
 */
public final class WakeupTokens {

    /** 内部唤醒请求头。 */
    public static final String HEADER = "X-Wakeup-Token";

    /** 文档占位串；配成正式令牌等于裸奔。 */
    private static final Set<String> INSECURE_PLACEHOLDERS = Set.of(
            "change-me",
            "change-me-wakeup",
            "replace_with_agentscope_wakeup_token");

    private WakeupTokens() {
    }

    /** 是否内部唤醒路径（已去 context path）。 */
    public static boolean isInternalWakeup(String path) {
        return "/api/internal/wakeups".equals(path);
    }

    /** 是否为文档占位串。 */
    public static boolean isInsecurePlaceholder(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return INSECURE_PLACEHOLDERS.contains(token.trim().toLowerCase(Locale.ROOT));
    }

    /** 启动校验：非空且非占位。 */
    public static void requireSecure(String name, String token) {
        String label = name == null || name.isBlank() ? "wakeup token" : name.trim();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(label + " is required when wakeup is enabled");
        }
        if (isInsecurePlaceholder(token)) {
            throw new IllegalStateException(label + " must not use a documented placeholder");
        }
    }

    /** 定长比较；空或占位期望值一律不通过。 */
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
