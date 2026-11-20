package com.zhu.access.application.auth.session;

/**
 * 登录会话在 Redis 里的短文本编解码。不放密码，也不放 JWT。
 */
public final class LoginSessionCodec {

    /** 缓存键前缀。后面跟不透明令牌。 */
    public static final String KEY_PREFIX = "login-session:";

    private static final char SEP = '\n';

    private LoginSessionCodec() {
    }

    /** 拼缓存键。令牌为空时仍拼前缀，调用方先校验。 */
    public static String key(String token) {
        return KEY_PREFIX + (token == null ? "" : token);
    }

    /**
     * 租户和用户都不能含换行，否则解不开。
     * 空白标识拒绝，避免写成无效会话。
     */
    public static String encode(String tenantId, String userId) {
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()
                || tenantId.indexOf(SEP) >= 0 || userId.indexOf(SEP) >= 0) {
            throw new IllegalArgumentException("login identity is invalid");
        }
        return tenantId + SEP + userId;
    }

    /**
     * 解不开或任一段空白时返回 null。调用方按未登录处理。
     */
    public static Identity decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int at = raw.indexOf(SEP);
        if (at <= 0 || at >= raw.length() - 1) {
            return null;
        }
        if (raw.indexOf(SEP, at + 1) >= 0) {
            return null;
        }
        String tenantId = raw.substring(0, at);
        String userId = raw.substring(at + 1);
        if (tenantId.isBlank() || userId.isBlank()) {
            return null;
        }
        return new Identity(tenantId, userId);
    }

    /**
     * 登录会话还原出的租户和用户。
     *
     * @param tenantId 租户
     * @param userId   用户
     */
    public record Identity(String tenantId, String userId) {
    }
}
