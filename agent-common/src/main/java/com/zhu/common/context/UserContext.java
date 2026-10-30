package com.zhu.common.context;

/**
 * 当前请求的用户。用法与 {@link TenantContext} 相同，请求结束必须 {@link #clear()}。
 */
public final class UserContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    /** 绑定当前线程的用户。 */
    public static void set(String userId) {
        CURRENT.set(userId);
    }

    /** 当前线程的用户，未设置时为 null。 */
    public static String get() {
        return CURRENT.get();
    }

    /** 请求结束时必须调用，避免线程池复用串用户。 */
    public static void clear() {
        CURRENT.remove();
    }
}
